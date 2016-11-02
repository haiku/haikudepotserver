/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.security.MessageDigest;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * <p>This service is able to provide the ability to authenticate a user given their nickname and their clear-text
 * password.  It will maintain a cache of nickname to {@link ObjectId}s so that it is able to lookup users very quickly
 * if they are known to this instance.  This may be useful in a small-scale deployment.  This class is accessed by
 * the {@link AuthenticationFilter}.</p>
 */

@Service
public class AuthenticationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(AuthenticationService.class);

    private final static String SUFFIX_JSONWEBTOKEN_SUBJECT = "@hds";

    private final static Pattern PATTERN_JSONWEBTOKEN_ISSUER = Pattern.compile("^[a-z0-9]+\\.hds$");

    /**
     * <p>This secret is used to sign a token containing username / password / time for token-based authentication.
     * </p>
     */

    @Value("${authentication.jws.sharedkey:}")
    private String jsonWebTokenSharedKey;

    @Value("${authentication.jws.expiryseconds:300}")
    private Integer jsonWebTokenExpirySeconds;

    @Value("${authentication.jws.issuer}")
    private String jsonWebTokenIssuer;

    @Resource
    private ServerRuntime serverRuntime;

    private JWSSigner jsonWebTokenSigner = null;

    private JWSVerifier jsonWebTokenVerifier = null;

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

    public ServerRuntime getServerRuntime() {
        return serverRuntime;
    }

    public void setServerRuntime(ServerRuntime serverRuntime) {
        this.serverRuntime = serverRuntime;
    }

    public Optional<ObjectId> authenticateByNicknameAndPassword(String nickname, String passwordClear) {

        Optional<ObjectId> result = Optional.empty();

        if(!Strings.isNullOrEmpty(nickname) && !Strings.isNullOrEmpty(passwordClear)) {

            ObjectContext objectContext = serverRuntime.getContext();

            Optional<User> userOptional = User.getByNickname(objectContext, nickname);

            if(userOptional.isPresent()) {
                User user = userOptional.get();
                String hash = hashPassword(user, passwordClear);

                if(hash.equals(user.getPasswordHash())) {
                    result = Optional.ofNullable(userOptional.get().getObjectId());
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
     * <p>This method will hash the password in a consistent manner across the whole system.</p>
     */

    public String hashPassword(User user, String passwordClear) {
        byte[] saltBytes = BaseEncoding.base16().decode(user.getPasswordSalt().toUpperCase());

        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
            sha.update(passwordClear.getBytes(Charsets.UTF_8));
        }
        catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("no SHA-256 crypt algorithm available",e);
        }

        sha.update(saltBytes);

        return BaseEncoding.base16().encode(sha.digest()).toLowerCase();
    }

    private int countMatches(String s, CharToBooleanFunction fn) {
        int length = s.length();
        int count = 0;
        for(int i=0;i<length;i++) {
            char c = s.charAt(i);
            if(fn.test(c)) {
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

    public boolean validatePassword(String passwordClear) {
        Preconditions.checkArgument(null != passwordClear, "the password clear must be supplied");

        if (passwordClear.length() < 8) {
            return false;
        }

        // get a count of digits - should be at least two.
        if (countMatches(passwordClear, new CharToBooleanFunction() { public boolean test(char c) { return c >= 48 && c <= 57; } }) < 2) {
            return false;
        }

        // get a count of upper case letters - should be at least one.
        if (countMatches(passwordClear, new CharToBooleanFunction() { public boolean test(char c) { return c >= 65 && c <= 90; } }) < 1) {
            return false;
        }

        return true;
    }

    private interface CharToBooleanFunction {
        boolean test(char c);
    }

    // ---------------------------
    // JSON WEB TOKEN

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
                    null==issueTime
                            || null==expirationTime
                            || nowMillis < issueTime.getTime()
                            || nowMillis > expirationTime.getTime()) {
                LOGGER.info("rejected jwt authentication; the issue time or expiration time are invalid or do not contain the current time");
            }
            else {
                String subject = claimsSet.getSubject();

                if (
                        null==subject
                                || !subject.endsWith(SUFFIX_JSONWEBTOKEN_SUBJECT)
                                || subject.length() <= SUFFIX_JSONWEBTOKEN_SUBJECT.length()) {
                    LOGGER.info("rejected jwt authentication; bad subject");
                }
                else {

                    String nickname = subject.substring(0,subject.length() - SUFFIX_JSONWEBTOKEN_SUBJECT.length());
                    ObjectContext context = serverRuntime.getContext();
                    Optional<User> userOptional = User.getByNickname(context, nickname);

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
        if (null!=payload && 0!=payload.length()) {
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

    public String generateToken(User user) {

        Preconditions.checkArgument(null != user, "the user must be provided");

        Instant instant = Instant.now();

        JWTClaimsSet claimsSet = new JWTClaimsSet
                .Builder()
                .subject(user.getNickname() + SUFFIX_JSONWEBTOKEN_SUBJECT)
                .issueTime(new java.util.Date(instant.toEpochMilli()))
                .expirationTime(new java.util.Date(instant.plus(jsonWebTokenExpirySeconds, ChronoUnit.SECONDS).toEpochMilli()))
                .issuer(jsonWebTokenIssuer)
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);

        try {
            signedJWT.sign(jsonWebTokenSigner);
        }
        catch(JOSEException je) {
            throw new IllegalStateException("unable to sign a jwt",je);
        }

        return signedJWT.serialize();
    }


}
