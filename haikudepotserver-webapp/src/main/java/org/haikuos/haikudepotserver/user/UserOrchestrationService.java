/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.user;

import com.google.common.base.*;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.cayenne.query.SelectQuery;
import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.entry.*;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.message.ModifyRequest;
import org.apache.directory.api.ldap.model.message.ModifyRequestImpl;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.support.cayenne.LikeHelper;
import org.haikuos.haikudepotserver.support.ldap.LdapConnectionPoolHolder;
import org.haikuos.haikudepotserver.user.model.LdapPerson;
import org.haikuos.haikudepotserver.user.model.UserSearchSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;

/**
 * <p>This service undertakes non-trivial operations on users.</p>
 */

@Service
public class UserOrchestrationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserOrchestrationService.class);

    /**
     * <p>Users are processed in batches to avoid excessive memory or IO consumption at once.  This constant
     * determines the size of the batches.</p>
     */

    private final static int BATCH_SIZE_USER = 20;

    private final static String LDAP_ATTRIBUTE_KEY_CN = "cn"; // common name
    private final static String LDAP_ATTRIBUTE_KEY_SN = "sn"; // surname
    private final static String LDAP_ATTRIBUTE_KEY_MAIL = "mail"; // mail
    private final static String LDAP_ATTRIBUTE_KEY_UID = "uid"; // uid
    private final static String LDAP_ATTRIBUTE_KEY_USERPASSWORD = "userPassword"; // contains a hash
    private final static String LDAP_ATTRIBUTE_KEY_OBJECTCLASS = "ObjectClass";

    private final static String LDAP_ATTRIBUTE_VALUE_OBJECTCLASS_INETORGPERSON = "inetOrgPerson";

    @Resource
    private LdapConnectionPoolHolder ldapConnectionPoolHolder;

    @Value("${ldap.people.dn:}")
    private String ldapPeopleDn;

    @PostConstruct
    public void init() {
        if(null!=ldapConnectionPoolHolder.get()) {
            if(Strings.isNullOrEmpty(ldapPeopleDn)) {
                throw new IllegalStateException("the ldap people DN must be configured if LDAP is to be used to back users; data");
            }
        }
    }

    // ------------------------------
    // LDAP

    /**
     * <p>LDAP may or may not be configured.  This method will return true if the system is
     * configured for interaction with LDAP.</p>
     */

    public boolean isLdapConfigured() {
        return null!=ldapConnectionPoolHolder.get();
    }

    private LdapPerson createPerson(Entry entry) throws LdapException {

        Attribute mailA = entry.get(LDAP_ATTRIBUTE_KEY_MAIL);
        Attribute uidA = entry.get(LDAP_ATTRIBUTE_KEY_UID);
        Attribute userPasswordA = entry.get(LDAP_ATTRIBUTE_KEY_USERPASSWORD);

        LdapPerson ldapPerson = new LdapPerson();
        ldapPerson.setDn(entry.getDn());
        ldapPerson.setMail(null == mailA ? null : mailA.getString());
        ldapPerson.setCn(entry.get(LDAP_ATTRIBUTE_KEY_CN).getString());
        ldapPerson.setSn(entry.get(LDAP_ATTRIBUTE_KEY_SN).getString());
        ldapPerson.setUid(null == uidA ? null : uidA.getString());
        ldapPerson.setUserPassword(null == userPasswordA ? null : new String(userPasswordA.getBytes(), Charsets.US_ASCII));

        return ldapPerson;
    }

    private Optional<LdapPerson> ldapFindPerson(LdapConnection ldapConnection, final String nickname) throws org.haikuos.haikudepotserver.user.LdapException {
        Preconditions.checkNotNull(ldapConnection);
        Preconditions.checkNotNull(nickname);
        Preconditions.checkState(User.NICKNAME_PATTERN.matcher(nickname).matches(),"the nickname is illegal");

        LdapPerson ldapPerson = null;

        try {
            EntryCursor cursor = ldapConnection.search(
                    String.format("%s=%s, %s", LDAP_ATTRIBUTE_KEY_CN, nickname, ldapPeopleDn),
                    String.format("(%s=%s)", LDAP_ATTRIBUTE_KEY_OBJECTCLASS, LDAP_ATTRIBUTE_VALUE_OBJECTCLASS_INETORGPERSON),
                    SearchScope.OBJECT);

            if (cursor.next()) {
                ldapPerson = createPerson(cursor.get());
            }

            if (cursor.next()) {
                throw new IllegalStateException("found two matches for the user; " + nickname);
            }
        }
        catch(LdapException le) {
            throw new org.haikuos.haikudepotserver.user.LdapException("an error arose finding the user in the ldap system; " + nickname, le);
        }
        catch(CursorException ce) {
            throw new org.haikuos.haikudepotserver.user.LdapException("an error arose finding the user in the ldap system; " + nickname, ce);
        }

        return Optional.fromNullable(ldapPerson);
    }

    private void accumulateModification(
            List<Modification> modifications,
            String attributeName,
            String existingValue,
            String newValue) {
        Modification modification = createModification(attributeName,existingValue,newValue);

        if(null!=modification) {
            modifications.add(modification);
        }
    }

    private Modification createModification(
            String attributeName,
            String existingValue,
            String newValue) {

        Preconditions.checkState(!Strings.isNullOrEmpty(attributeName));

        boolean existingN = !Strings.isNullOrEmpty(existingValue);
        boolean newN = !Strings.isNullOrEmpty(newValue);

        if(existingN != newN) {

            if(existingN) {
                return new DefaultModification(ModificationOperation.REMOVE_ATTRIBUTE, attributeName, existingValue);
            }
            else {
                return new DefaultModification(ModificationOperation.ADD_ATTRIBUTE, attributeName, newValue);
            }

        }
        else {
            if(newN && !existingValue.equals(newValue)) {
                return new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, attributeName, newValue);
            }
            else {
                LOGGER.trace("new and old attribute values are the same; will not need to replace them");
            }
        }

        return null;
    }

    /**
     * <p>This method will update the supplied user in the supplied object context with the LDAP server.</p>
     */

    public void ldapUpdateUser(ObjectContext context, User user) throws org.haikuos.haikudepotserver.user.LdapException {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(user);

        if(user.getNickname().equals(User.NICKNAME_ROOT)) {
            LOGGER.info("will not update the root user to ldap");
        }
        else {
            if (isLdapConfigured()) {

                LdapConnection ldapConnection = null;

                try {
                    ldapConnection = ldapConnectionPoolHolder.get().getConnection();
                    Optional<LdapPerson> ldapPersonOptional = ldapFindPerson(ldapConnection, user.getNickname());

                    if (!user.getActive()) {

                        if (ldapPersonOptional.isPresent()) {
                            ldapConnection.delete(ldapPersonOptional.get().getDn());
                            LOGGER.info("did delete ldap directory data for user; " + user.toString());
                        } else {
                            LOGGER.debug("no need take any action for inactive user {}; there is no data present in the ldap directory", user.toString());
                        }

                    } else {

                        if (ldapPersonOptional.isPresent()) {

                            ModifyRequest modifyRequest = new ModifyRequestImpl();
                            modifyRequest.setName(ldapPersonOptional.get().getDn());

                            List<Modification> modifications = Lists.newArrayList();

                            accumulateModification(modifications, LDAP_ATTRIBUTE_KEY_CN, ldapPersonOptional.get().getCn(), user.getNickname());
                            accumulateModification(modifications, LDAP_ATTRIBUTE_KEY_SN, ldapPersonOptional.get().getSn(), user.getNickname());
                            accumulateModification(modifications, LDAP_ATTRIBUTE_KEY_UID, ldapPersonOptional.get().getUid(), user.getNickname());
                            accumulateModification(modifications, LDAP_ATTRIBUTE_KEY_USERPASSWORD, ldapPersonOptional.get().getUserPassword(), user.toLdapUserPasswordAttributeValue());
                            accumulateModification(modifications, LDAP_ATTRIBUTE_KEY_MAIL, ldapPersonOptional.get().getMail(), user.getEmail());

                            if (!modifications.isEmpty()) {
                                ldapConnection.modify(
                                        ldapPersonOptional.get().getDn(),
                                        modifications.toArray(new Modification[modifications.size()])
                                );
                                LOGGER.info("did update ldap directory entry for; {}", user.toString());
                            }

                        } else {

                            DefaultEntry defaultEntry = new DefaultEntry(String.format("%s=%s,%s", LDAP_ATTRIBUTE_KEY_CN, user.getNickname(), ldapPeopleDn));

                            defaultEntry.add(new DefaultAttribute(LDAP_ATTRIBUTE_KEY_OBJECTCLASS, LDAP_ATTRIBUTE_VALUE_OBJECTCLASS_INETORGPERSON));

                            defaultEntry.add(new DefaultAttribute(LDAP_ATTRIBUTE_KEY_CN, user.getNickname()));
                            defaultEntry.add(new DefaultAttribute(LDAP_ATTRIBUTE_KEY_SN, user.getNickname()));
                            defaultEntry.add(new DefaultAttribute(LDAP_ATTRIBUTE_KEY_UID, user.getNickname()));
                            defaultEntry.add(new DefaultAttribute(LDAP_ATTRIBUTE_KEY_USERPASSWORD, user.toLdapUserPasswordAttributeValue()));

                            if (!Strings.isNullOrEmpty(user.getEmail())) {
                                defaultEntry.add(new DefaultAttribute(LDAP_ATTRIBUTE_KEY_MAIL, user.getEmail()));
                            }

                            ldapConnection.add(defaultEntry);
                            LOGGER.info("did create ldap directory entry for; {}", user.toString());
                        }

                    }

                } catch (Exception le) {
                    throw new org.haikuos.haikudepotserver.user.LdapException("unable to update user to ldap directory; " + user.getNickname(), le); // TODO; better exception handling
                } finally {
                    if (null != ldapConnection) {
                        try {
                            ldapConnectionPoolHolder.get().releaseConnection(ldapConnection);
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }

            } else {
                LOGGER.debug("ldap is not configured --> did not update ldap for user {}", user.toString());
            }
        }

    }

    /**
     * <p>This method will go through all of the users in the database and will synchronize them all with the
     * LDAP directory; adding, modifying and deleting entries as necessary.</p>
     */

    public void ldapSynchronizeAllUsers(ObjectContext context) throws org.haikuos.haikudepotserver.user.LdapException {
        Preconditions.checkNotNull(context);

        if(isLdapConfigured()) {

            LOGGER.info("will update all users to ldap");

            SelectQuery query = new SelectQuery(User.class);
            query.setFetchLimit(BATCH_SIZE_USER);
            query.setFetchOffset(0);
            Integer lastCount = null;
            int count = 0;

            while (null == lastCount || lastCount >= BATCH_SIZE_USER) {

                if (null != lastCount) {
                    query.setFetchOffset(query.getFetchOffset() + BATCH_SIZE_USER);
                }

                @SuppressWarnings("unchecked") List<User> userBatch = context.performQuery(query);
                lastCount = userBatch.size();

                for (User user : userBatch) {
                    ldapUpdateUser(context, user);
                }

                count += lastCount;
            }

            LOGGER.info("did update {} users to ldap", count);

        }
        else {
            LOGGER.info("ldap is not configured --> will not update all users to ldap");
        }
    }

    /**
     * <p>This method will search the LDAP directory at the root for people and will find all of the users.  If there
     * exists some users who do not exist in the database then those users will be deleted from the LDAP server.</p>
     */

    // it is not clear if this will be necessary, but is probably a good idea to avoid people adding spurious data
    // into the HDS LDAP directory.

    public void ldapRemoveNonExistentUsers(ObjectContext context) throws org.haikuos.haikudepotserver.user.LdapException {

        Preconditions.checkNotNull(context);

        if(isLdapConfigured()) {

            LdapConnection ldapConnection = null;

            try {
                ldapConnection = ldapConnectionPoolHolder.get().getConnection();

                EntryCursor cursor = ldapConnection.search(
                        ldapPeopleDn,
                        String.format("(%s=%s)", LDAP_ATTRIBUTE_KEY_OBJECTCLASS, LDAP_ATTRIBUTE_VALUE_OBJECTCLASS_INETORGPERSON),
                        SearchScope.ONELEVEL);

                // could be more efficient, but probably does not matter at this stage.

                while(cursor.next()) {
                    LdapPerson person = createPerson(cursor.get());
                    Optional<User> userOptional = User.getByNickname(context, person.getCn());

                    if(!userOptional.isPresent() || !userOptional.get().getActive()) {
                        LOGGER.info("will delete ldap directory entry as no active user can be found for; {}", person.getCn());
                        ldapConnection.delete(person.getDn());
                        LOGGER.info("did delete ldap directory entry for; {}", person.getCn());
                    }
                    else {
                        LOGGER.trace("have found active user for person {}; will not remove", person.getCn());
                    }

                }

            } catch (Exception le) {
                throw new org.haikuos.haikudepotserver.user.LdapException("unable to remove non-existent users from the ldap directory", le); // TODO; better exception handling
            } finally {
                if (null != ldapConnection) {
                    try {
                        ldapConnectionPoolHolder.get().releaseConnection(ldapConnection);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }
        else {
            LOGGER.info("ldap is not configured --> will not remove non-existent users from ldap directory");
        }

    }

    // ------------------------------
    // DATABASE SEARCH

    private String prepareWhereClause(
            List<Object> parameterAccumulator,
            ObjectContext context,
            UserSearchSpecification search) {

        Preconditions.checkNotNull(parameterAccumulator);
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        Preconditions.checkState(search.getOffset() >= 0);
        Preconditions.checkState(search.getLimit() > 0);

        List<String> parts = Lists.newArrayList();

        if(!Strings.isNullOrEmpty(search.getExpression())) {
            switch(search.getExpressionType()) {

                case CONTAINS:
                    parts.add("LOWER(u.nickname) LIKE ?" + (parameterAccumulator.size() + 1));
                    parameterAccumulator.add("%" + LikeHelper.ESCAPER.escape(search.getExpression()) + "%");
                    break;

                default:
                    throw new IllegalStateException("unknown expression type " + search.getExpressionType().name());

            }
        }

        if(!search.getIncludeInactive()) {
            parts.add("u.active = ?" + (parameterAccumulator.size() + 1));
            parameterAccumulator.add(Boolean.TRUE);
        }

        return Joiner.on(" AND ").join(parts);
    }

    /**
     * <p>Undertakes a search for users.</p>
     */

    @SuppressWarnings("unchecked")
    public List<User> search(
            ObjectContext context,
            UserSearchSpecification searchSpecification) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(searchSpecification);

        StringBuilder queryBuilder = new StringBuilder("SELECT u FROM User AS u");
        List<Object> parameters = Lists.newArrayList();
        String where = prepareWhereClause(parameters, context, searchSpecification);

        if(!Strings.isNullOrEmpty(where)) {
            queryBuilder.append(" WHERE ");
            queryBuilder.append(where);
        }

        queryBuilder.append(" ORDER BY u.nickname ASC");

        EJBQLQuery query = new EJBQLQuery(queryBuilder.toString());

        for (int i = 0; i < parameters.size(); i++) {
            query.setParameter(i+1, parameters.get(i));
        }

        query.setFetchOffset(searchSpecification.getOffset());
        query.setFetchLimit(searchSpecification.getLimit());

        //noinspection unchecked
        return (List<User>) context.performQuery(query);
    }

    /**
     * <p>Find out the total number of results that would be yielded from
     * a search if the search were not constrained.</p>
     */

    public long total(
            ObjectContext context,
            UserSearchSpecification searchSpecification) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(searchSpecification);

        StringBuilder queryBuilder = new StringBuilder("SELECT COUNT(u) FROM User u");
        List<Object> parameters = Lists.newArrayList();
        String where = prepareWhereClause(parameters, context, searchSpecification);

        if(!Strings.isNullOrEmpty(where)) {
            queryBuilder.append(" WHERE ");
            queryBuilder.append(where);
        }

        EJBQLQuery query = new EJBQLQuery(queryBuilder.toString());

        for (int i = 0; i < parameters.size(); i++) {
            query.setParameter(i+1, parameters.get(i));
        }

        @SuppressWarnings("unchecked") List<Long> result = (List<Long>) context.performQuery(query);

        switch(result.size()) {

            case 1:
                return result.get(0);

            default:
                throw new IllegalStateException("the result should have contained a single long result");

        }
    }

    // ------------------------------


}
