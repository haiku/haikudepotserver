/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.security.model.UserAuthenticationService;
import org.haiku.haikudepotserver.user.model.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;


@Service
public class UserAuthenticationServiceImpl implements UserAuthenticationService {

    protected static final Logger LOGGER = LoggerFactory.getLogger(UserAuthenticationServiceImpl.class);

    private final static String SUFFIX_JSONWEBTOKEN_SUBJECT = "@hds";

    private final static Pattern PATTERN_JSONWEBTOKEN_ISSUER = Pattern.compile("^[a-z0-9]+\\.hds$");

    private final ServerRuntime serverRuntime;

    private final UserService userService;

    /**
     * <p>This secret is used to sign a token containing username / password / time for token-based authentication.
     * </p>
     */

    private final Integer jsonWebTokenExpirySeconds;
    private final String jsonWebTokenIssuer;

    private final PasswordEncoder passwordEncoder;

    private JWSSigner jsonWebTokenSigner = null;
    private JWSVerifier jsonWebTokenVerifier = null;
    private String jsonWebTokenSharedKey;

    public UserAuthenticationServiceImpl(
            ServerRuntime serverRuntime,
            UserService userService,
            PasswordEncoder passwordEncoder,
            @Value("${hds.authentication.jws.shared-key:}") String jsonWebTokenSharedKey,
            @Value("${hds.authentication.jws.expiry-seconds:300}") Integer jsonWebTokenExpirySeconds,
            @Value("${hds.authentication.jws.issuer}") String jsonWebTokenIssuer) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.jsonWebTokenExpirySeconds = Preconditions.checkNotNull(jsonWebTokenExpirySeconds);
        this.jsonWebTokenIssuer = Preconditions.checkNotNull(jsonWebTokenIssuer);
        this.jsonWebTokenSharedKey = StringUtils.isNotBlank(jsonWebTokenSharedKey)
            ? jsonWebTokenSharedKey : UUID.randomUUID().toString();
    }

    @PostConstruct
    public void init() {

        if(jsonWebTokenExpirySeconds <= 15) {
            throw new IllegalStateException("the expiry seconds for the json web token is too small");
        }

        if(Strings.isNullOrEmpty(jsonWebTokenIssuer) || !PATTERN_JSONWEBTOKEN_ISSUER.matcher(jsonWebTokenIssuer).matches()) {
            throw new IllegalStateException("the json web token issuer is malformed");
        }

        if(!Strings.isNullOrEmpty(jsonWebTokenSharedKey)) {
            jsonWebTokenSharedKey = jsonWebTokenSharedKey.trim();

            if(jsonWebTokenSharedKey.length() < 10) {
                throw new IllegalStateException("the json web token shared key length is too small to be secure");
            }
        }

        if(Strings.isNullOrEmpty(jsonWebTokenSharedKey)) {
            jsonWebTokenSharedKey = UUID.randomUUID().toString();
            LOGGER.warn("a shared key is not supplied so a random one has been created");
        }

        try {
            jsonWebTokenSigner = new MACSigner(jsonWebTokenSharedKey.getBytes(Charsets.UTF_8));
            jsonWebTokenVerifier = new MACVerifier(jsonWebTokenSharedKey.getBytes(Charsets.UTF_8));
        }
        catch(JOSEException je) {
            throw new RuntimeException("unable to create the signer / verifier for JWT tokens", je);
        }
    }

    @Override
    public Optional<ObjectId> authenticateByNicknameAndPassword(String nickname, String passwordClear) {
        Optional<ObjectId> result = Optional.empty();

        if (!Strings.isNullOrEmpty(nickname) && !Strings.isNullOrEmpty(passwordClear)) {
            ObjectContext objectContext = serverRuntime.newContext();
            Optional<User> userOptional = User.tryGetByNickname(objectContext, nickname);

            if (userOptional.isPresent()) {
                User user = userOptional.get();

                if (matchPassword(user, passwordClear)) {
                    result = Optional.ofNullable(userOptional.get().getObjectId());
                    maybeUpdateLastAuthenticationTimestamp(objectContext, user);
                }
                else {
                    LOGGER.info("the authentication for the user; {} failed", nickname);
                }
            }
            else {
                LOGGER.info("unable to find the user; {}", nickname);
            }
        }
        else {
            LOGGER.info("attempt to authenticate with no username or no password");
        }

        return result;
    }

    /**
     * <p>This will update the time at which the user last authenticated with
     * the system.  It will only keep this timestamp at a resolution of an
     * hour.  It does this so that if the user is often authenticating, that
     * the system is not writing to the user table too often causing low-value
     * IO load.</p>
     */

    private void maybeUpdateLastAuthenticationTimestamp(
            ObjectContext context, User user) {
        long millisNow = Clock.systemUTC().millis();
        long millisStored = Optional.ofNullable(user.getLastAuthenticationTimestamp())
                .map(Timestamp::getTime)
                .orElse(0L);

        if (Math.abs(millisNow - millisStored) > TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS)) {
            user.setLastAuthenticationTimestamp(new java.sql.Timestamp(millisNow));
            context.commitChanges();
            LOGGER.debug("did store last authenticated timestamp for user [{}]", user.getNickname());
        }

    }

    @Override
    public void setPassword(User user, String passwordClear) {
        Preconditions.checkArgument(null != user, "the user is required");
        Preconditions.checkArgument(null != passwordClear, "the password is required");
        List<String> parts = Splitter.on(".").splitToList(passwordEncoder.encode(passwordClear));
        if (2 != parts.size()) {
            throw new IllegalStateException("expecting a salt and hash separated by a period symbol");
        }
        user.setPasswordSalt(parts.get(0));
        user.setPasswordHash(parts.get(1));
    }

    @Override
    public boolean matchPassword(User user, String passwordClear) {
        return StringUtils.isNotBlank(user.getPasswordSalt())
                && StringUtils.isNotBlank(user.getPasswordHash())
                && passwordEncoder.matches(
                passwordClear,
                user.getPasswordSalt() + "." + user.getPasswordHash());
    }

    private int countMatches(String s, CharToBooleanFunction fn) {
        int length = s.length();
        int count = 0;
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            if (fn.test(c)) {
                count++;
            }
        }
        return count;
    }

    /**
     * <p>Passwords should be hard to guess and so there needs to be a certain level of complexity to
     * them.  They should of a certain length and should contain some mix of letters and digits as well
     * as at least one upper case letter.</p>
     *
     * <p>This method will check the password for suitability.</p>
     */

    @Override
    public boolean validatePassword(String passwordClear) {
        Preconditions.checkArgument(null != passwordClear, "the password clear must be supplied");

        if (passwordClear.length() < 8) {
            return false;
        }

        // get a count of digits - should be at least two.
        if (countMatches(passwordClear, c -> c >= 48 && c <= 57) < 2) {
            return false;
        }

        // get a count of upper case letters - should be at least one.
        if (countMatches(passwordClear, c -> c >= 65 && c <= 90) < 1) {
            return false;
        }

        return true;
    }

    private interface CharToBooleanFunction {
        boolean test(char c);
    }

    // ---------------------------
    // JSON WEB TOKEN

    @Override
    public Optional<ObjectId> authenticateByToken(String payload) {
        Optional<SignedJWT> signedJwtOptional = verifyToken(payload);

        if (signedJwtOptional.isPresent()) {
            return authenticate(signedJwtOptional.get());
        }

        return Optional.empty();
    }

    /**
     * <p>This method will validate the json web token and assuming that everything is OK, it will return
     * an ObjectId that refers to the </p>
     */

    private Optional<ObjectId> authenticate(SignedJWT signedJwt) {

        Preconditions.checkArgument(null != signedJwt, "the JWT must be provided");

        JWTClaimsSet claimsSet;
        long nowMillis = System.currentTimeMillis();

        try {
            claimsSet = signedJwt.getJWTClaimsSet();
        }
        catch (ParseException pe) {
            throw new IllegalStateException("unable to parse the jwt",pe);
        }

        String issuer = claimsSet.getIssuer();

        if (null==issuer||!issuer.equals(jsonWebTokenIssuer)) {
            LOGGER.info("rejected jwt authentication; the issuer '{}' on the jwt does not match the expected '{}'", issuer, jsonWebTokenIssuer);
        }
        else {
            java.util.Date issueTime = claimsSet.getIssueTime();
            java.util.Date expirationTime = claimsSet.getExpirationTime();

            if (
                    null == issueTime
                            || null == expirationTime
                            || nowMillis < issueTime.getTime()
                            || nowMillis > expirationTime.getTime()) {
                LOGGER.info("rejected jwt authentication; the issue time or expiration time are invalid or do not contain the current time");
            }
            else {
                String subject = claimsSet.getSubject();

                if (
                        null == subject
                                || !subject.endsWith(SUFFIX_JSONWEBTOKEN_SUBJECT)
                                || subject.length() <= SUFFIX_JSONWEBTOKEN_SUBJECT.length()) {
                    LOGGER.info("rejected jwt authentication; bad subject");
                }
                else {

                    String nickname = subject.substring(0,subject.length() - SUFFIX_JSONWEBTOKEN_SUBJECT.length());
                    ObjectContext context = serverRuntime.newContext();
                    Optional<User> userOptional = User.tryGetByNickname(context, nickname);

                    if (userOptional.isPresent()) {
                        return Optional.of(userOptional.get().getObjectId());
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * <p>This method will take a JWT payload and will check the signing.  If the signing and format
     * of the payload is OK then it will return the payload parsed; otherwise it will return
     * an absent.</p>
     */

    private Optional<SignedJWT> verifyToken(String payload) {
        if (null != payload && 0 != payload.length()) {
            try {
                SignedJWT signedJWT = SignedJWT.parse(payload);

                try {
                    if (signedJWT.verify(jsonWebTokenVerifier)) {
                        return Optional.of(signedJWT);
                    }
                    else {
                        LOGGER.error("attempt to use jwt that was unable to be verified");
                    }
                }
                catch (JOSEException je) {
                    throw new IllegalStateException("unable to verify the jwt", je);
                }
            }
            catch (ParseException pe) {
                LOGGER.error("rejected malformed jwt that was unable to be parsed", pe);
            }
        }

        return Optional.empty();
    }

    /**
     * <p>This will return a JWT (java web token) that is signed by a secret that allows for the client to get
     * that token and for it to be used as a form of authentication for some period of time.</p>
     */

    @Override
    public String generateToken(User user) {

        Preconditions.checkArgument(null != user, "the user must be provided");

        Instant instant = Instant.now();

        JWTClaimsSet.Builder builder = new JWTClaimsSet
                .Builder()
                .subject(user.getNickname() + SUFFIX_JSONWEBTOKEN_SUBJECT)
                .issueTime(new java.util.Date(instant.toEpochMilli()))
                .expirationTime(new java.util.Date(instant.plus(jsonWebTokenExpirySeconds, ChronoUnit.SECONDS).toEpochMilli()))
                .issuer(jsonWebTokenIssuer);

        if (!user.getIsRoot() && !userService.isUserCurrentlyAgreeingToCurrentUserUsageConditions(user)) {
            builder.claim(CLAIM_REQUIRES_AGREE_USER_USAGE_CONDITIONS, true);
        }

        JWTClaimsSet claimsSet = builder.build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);

        try {
            signedJWT.sign(jsonWebTokenSigner);
        }
        catch(JOSEException je) {
            throw new IllegalStateException("unable to sign a jwt",je);
        }

        return signedJWT.serialize();
    }

    @Override
    public Optional<Pair<String, String>> tryExtractCredentialsFromBasicAuthorizationHeader(String header) {
        return Optional.ofNullable(header)
                .filter(h -> h.startsWith("Basic "))
                .map(h -> h.substring(6))
                .map(s -> new String(Base64.getDecoder().decode(s), Charsets.UTF_8))
                .map(s -> {
                    int colonIndex = s.indexOf(":");

                    if (-1 == colonIndex) {
                        return null;
                    }

                    return Pair.of(s.substring(0, colonIndex), s.substring(colonIndex + 1));
                });
    }

}
