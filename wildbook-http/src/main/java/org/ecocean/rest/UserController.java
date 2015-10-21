package org.ecocean.rest;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.ecocean.ContextConfiguration;
import org.ecocean.Global;
import org.ecocean.email.EmailUtils;
import org.ecocean.security.User;
import org.ecocean.security.UserFactory;
import org.ecocean.security.UserToken;
import org.ecocean.servlet.ServletUtils;
import org.ecocean.util.LogBuilder;
import org.ecocean.util.WildbookUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.samsix.database.Database;
import com.samsix.database.DatabaseException;

import de.neuland.jade4j.exceptions.JadeCompilerException;
import de.neuland.jade4j.exceptions.JadeException;

@RestController
@RequestMapping(value = "/obj/user")
public class UserController {

    private static Logger logger = LoggerFactory.getLogger(MediaSubmissionController.class);

    public static boolean notAcceptedTerms(final User user) {
        return (Global.INST.getAppResources().getBoolean("user.agreement.show", false)
                && (Global.INST.getAppResources().getString("user.agreement.url", null) != null)
                && ! user.getAcceptedUserAgreement());
    }


//    public static UserToken getUserToken(final HttpServletRequest request,
//                                         final String username,
//                                         final String password) throws DatabaseException {
//        try (Database db = ServletUtils.getDb(request)) {
//            User user = UserFactory.getUserByNameOrEmail(db, username);
//
//            Account acc = Stormpath.getAccount(username);
//
//            if (acc == null) {
//                if (user == null) {
//                    return null;
//                }
//
//                String hashedPass = ServletUtilities.hashAndSaltPassword(password, user.getSalt());
//                return new UserToken(user, new UsernamePasswordToken(user.getUserId().toString(), hashedPass));
//            }
//
//            String hashedPass;
//            try {
//                Stormpath.loginAccount(username, password);
//                //
//                // If they got their user through stormpath then we trust their
//                // password to be correct so we use the value from the db. Should be the
//                // same as that on stormpath?
//                //
//                if (user == null) {
//                    if (logger.isDebugEnabled()) {
//                        logger.debug("successful authentication via Stormpath, but no Wildbook user for email "
//                                + acc.getEmail()
//                                + ". creating one!");
//                    }
//                    user = Stormpath.createUserFromStormpath(acc, password);
//                    UserFactory.saveUser(db, user);
//                }
//
//                hashedPass = user.getHashedPass();
//            } catch (ResourceException ex) {
//                logger.warn("failed to authenticate user '"
//                        + username
//                        + "' via Stormpath; falling back to Wildbook User: "
//                        + ex.toString());
//                hashedPass = ServletUtilities.hashAndSaltPassword(password, user.getSalt());
//            }
//
//            return new UserToken(user, new UsernamePasswordToken(user.getUserId().toString(), hashedPass));
//        }
//    }


    public static UserToken getUserToken(final HttpServletRequest request,
                                         final String username,
                                         final String password) throws DatabaseException {
        try (Database db = ServletUtils.getDb(request)) {
            User user = UserFactory.getUserByNameOrEmail(db, username);

            if (user == null) {
                return null;
            }

            String hashedPass = WildbookUtils.hashAndSaltPassword(password, user.getSalt());
            return new UserToken(user, new UsernamePasswordToken(user.getUserId().toString(), hashedPass));
        }
    }


    public static String getAllRolesForUserAsString(final HttpServletRequest request,
                                                    final Integer userid) throws DatabaseException {
        if (userid == null) {
            return "";
        }

        try (Database db = ServletUtils.getDb(request)) {
            StringBuilder rolesFound = new StringBuilder();
            db.getTable(UserFactory.TABLENAME_ROLES).select((rs) -> {
                String contextName = ContextConfiguration.getNameForContext(rs.getString("context"));
                rolesFound.append(contextName+":" + rs.getString("rolename") + "\r");
            }, "userid = " + userid);
            return rolesFound.toString();
        }
    }


    @RequestMapping(value = "isloggedin", method = RequestMethod.GET)
    public static SimpleUser isLoggedIn(final HttpServletRequest request) throws DatabaseException {
        if (request.getUserPrincipal() == null) {
            return null;
        }

        Integer userid = NumberUtils.createInteger(request.getUserPrincipal().getName());

        try (Database db = ServletUtils.getDb(request)) {
            return UserFactory.getUser(db, userid);
        }
    }


    @RequestMapping(value = "login", method = RequestMethod.POST)
    public SimpleUser loginCall(final HttpServletRequest request,
                                @RequestBody
                                final LoginAttempt loginAttempt) throws DatabaseException
    {
        UserToken userToken = getUserToken(request, loginAttempt.username, loginAttempt.password);

        if (userToken == null) {
            throw new SecurityException("No user with username [" + loginAttempt.username + "] is found.");
        }

        try {
            Subject subject = SecurityUtils.getSubject();
            subject.login(userToken.getToken());

            userToken.getUser().setLastLogin(System.currentTimeMillis());
            try (Database db = ServletUtils.getDb(request)) {
                UserFactory.saveUser(db, userToken.getUser());
            }

            return userToken.getUser().toSimple();
        } finally {
            userToken.clear();
        }
    }


    public static void logoutUser(final HttpServletRequest request) throws ServletException, IOException {
        Subject subject = SecurityUtils.getSubject();
        if (subject != null) {
            subject.logout();
        }

        HttpSession session = request.getSession(false);
        if( session != null ) {
            session.invalidate();
        }
    }


    /*
     * This doesn't work in the sense that from a different origin (a node server calling in restfully)
     * does not know that we are now logged out. We are logged out because going to wildbook verifies
     * that, but if we go to our node server and check isLoggedIn it thinks we are. Thus, the UserPrincipal
     * for that session is not getting nulled out. However, if we use this exact same line of code in
     * the servlet LogoutUser from this same node server, the UserPrincipal is nulled out. Something
     * bizarre going on there that I don't understand. Just means I'm hanging on to that LogoutUser
     * one-line servlet. :)
     */
//    @RequestMapping(value = "logout", method = RequestMethod.GET)
//    public void logoutCall(final HttpServletRequest request) throws ServletException, IOException
//    {
//        logoutUser(request);
//    }


    @RequestMapping(value = "verify", method = RequestMethod.POST)
    public UserVerify verifyEmail(final HttpServletRequest request,
                                  @RequestBody @Valid final UserInfo userInfo) throws DatabaseException {
        if (userInfo == null) {
            throw new IllegalArgumentException("Null argument passed.");
        }

        UserVerify verify = new UserVerify();

        try (Database db = ServletUtils.getDb(request)) {
            User user = UserFactory.getUserByEmail(db, userInfo.email);
            verify.newlyCreated = (user == null);

            if (user == null) {
                user = new User(null, null, userInfo.fullName, userInfo.email);
                user.initPassword(UUID.randomUUID().toString());
                //
                // Let's assume that new people have accepted the
                // user agreement. They should be saying yes to this as they log in anyway. Shouldn't
                // be a separate thing.
                //
                user.setAcceptedUserAgreement(true);

                UserFactory.saveUser(db, user);
            }

            verify.user = user.toSimple();
            verify.verified = user.isVerified();

            return verify;
        }
    }


    @RequestMapping(value = "sendpassreset", method = RequestMethod.POST)
    public void sendResetEmail(final HttpServletRequest request,
                               @RequestBody @Valid final String userameOrEmail) throws DatabaseException, IllegalAccessException, JadeCompilerException, AddressException, JadeException, IOException, MessagingException {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending reset email for address [" + userameOrEmail + "]");
        }

        try (Database db = ServletUtils.getDb(request)) {
            User user = UserFactory.getUserByNameOrEmail(db, userameOrEmail);
            if (user == null) {
                throw new IllegalAccessException("No user found with this email.");
            }

            String token = UserFactory.createPWResetToken(db, user.getUserId());

            Map<String, Object> model = EmailUtils.createModel();
            model.put(EmailUtils.TAG_USER, user.toSimple());
            model.put(EmailUtils.TAG_TOKEN, token);
            EmailUtils.sendJadeTemplate(EmailUtils.getAdminSender(),
                    user.getEmail(),
                    "account/passwordReset",
                    model);
        }
    }


    @RequestMapping(value = "resetpass", method = RequestMethod.POST)
    public void resetPassword(final HttpServletRequest request,
                              @RequestBody final ResetPass reset) throws DatabaseException, IllegalAccessException {
        if (logger.isDebugEnabled()) {
            logger.debug(LogBuilder.quickLog("token", reset.token));
            logger.debug(LogBuilder.quickLog("password", reset.password));
        }

        try (Database db = ServletUtils.getDb(request)) {
            User user = UserFactory.verifyPRToken(db, reset.token);

            user.resetPassword(reset.password);
            user.setVerified(true);
            UserFactory.saveUser(db, user);
        }
    }

    @RequestMapping(value = "verifypasstoken", method = RequestMethod.POST)
    public void verifyPassToken(final HttpServletRequest request,
                                @RequestBody @Valid final String token) throws IllegalAccessException, DatabaseException {
        try (Database db = ServletUtils.getDb(request)) {
            UserFactory.verifyPRToken(db, token);
        }
    }


    //
    // LEAVE: This is just a test url that allows us to see if we have the correct
    // setting in our dispatcher-servlet.xml that forces Spring to not make assumptions
    // about a file type of the return value if there is a dot in the path param.
    //
    @RequestMapping(value = "test/{email:.+}", method = RequestMethod.GET)
    public UserVerifyInfo test(final HttpServletRequest request,
                               @PathVariable("email") final String email) {
        UserVerifyInfo info = new UserVerifyInfo();
        info.email = email + " - test";
        return info;
    }


    static class UserInfo {
        public String email;
        public String fullName;
    }

    static class UserVerifyInfo {
        public String email;
        public UserVerifyInfo() {
        }
        public String getEmail() {
            return email;
        }
    }

    static class LoginAttempt {
        public String username;
        public String password;
    }

    static class UserVerify {
        public SimpleUser user;
        public boolean verified;
        public boolean newlyCreated;
    }

    static class ResetPass {
        public String token;
        public String password;
    }
}

