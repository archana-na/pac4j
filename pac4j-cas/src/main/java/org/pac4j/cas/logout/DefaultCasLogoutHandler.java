package org.pac4j.cas.logout;

import org.jasig.cas.client.session.SingleSignOutHandler;
import org.pac4j.cas.client.CasClient;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.store.GuavaStore;
import org.pac4j.core.store.Store;
import org.pac4j.core.util.CommonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * This class is the logout handler for the {@link CasClient}, inspired by the {@link SingleSignOutHandler} of the Apereo CAS client.
 *
 * @author Jerome Leleu
 * @since 1.4.0
 */
public class DefaultCasLogoutHandler<C extends WebContext> implements CasLogoutHandler<C> {

    protected static final Logger logger = LoggerFactory.getLogger(DefaultCasLogoutHandler.class);

    private Store<String, Object> store = new GuavaStore<>(10000, 1, TimeUnit.HOURS);

    private boolean killSession;

    @Override
    public void recordSession(final C context, final String ticket) {
        final SessionStore sessionStore = context.getSessionStore();
        final String sessionId = sessionStore.getOrCreateSessionId(context);
        final Optional<Object> trackableSession = sessionStore.getTrackableSession(context);

        if (trackableSession.isPresent()) {
            logger.debug("ticket: {} -> trackableSession: {}", ticket, trackableSession.get());
            logger.debug("sessionId: {}", sessionId);
            store.set(ticket, trackableSession);
            store.set(sessionId, ticket);
        } else {
            logger.debug("No trackable session for the current session store: {}", sessionStore);
        }
    }

    @Override
    public void destroySessionFront(final C context, final String ticket) {
        store.remove(ticket);

        final SessionStore sessionStore = context.getSessionStore();
        final String currentSessionId = sessionStore.getOrCreateSessionId(context);
        logger.debug("currentSessionId: {}", currentSessionId);
        final String sessionToTicket = (String) store.get(currentSessionId);
        logger.debug("-> ticket: {}", ticket);
        store.remove(currentSessionId);

        if (CommonHelper.areEquals(ticket, sessionToTicket)) {
            destroy(context, sessionStore);
        } else {
            logger.error("The user profiles (and session) can not be destroyed for CAS front channel logout because the provided ticket is not the same as the one linked to the current session");
        }
    }

    protected void destroy(final C context, final SessionStore sessionStore) {
        // remove profiles
        final ProfileManager manager = new ProfileManager(context);
        manager.logout();
        logger.debug("destroy the user profiles");
        // and optionally the web session
        if (killSession) {
            logger.debug("destroy the whole session");
            sessionStore.invalidateSession(context);
        }
    }

    @Override
    public void destroySessionBack(final C context, final String ticket) {
        final Object trackableSession = store.get(ticket);
        logger.debug("ticket: {} -> trackableSession: {}", ticket, trackableSession);
        if (trackableSession == null) {
            logger.error("No trackable session found for back channel logout. Either the session store does not support to track session or it has expired from the store and the store settings must be updated");
        } else {
            store.remove(ticket);

            // renew context with the original session store
            final SessionStore sessionStore = context.getSessionStore();
            final Optional<SessionStore> optNewSesionStore = sessionStore.buildFromTrackableSession(context, trackableSession);
            if (optNewSesionStore.isPresent()) {
                final SessionStore newSessionStore = optNewSesionStore.get();
                logger.debug("newSesionStore: {}", newSessionStore);
                context.setSessionStore(newSessionStore);
                final String sessionId = newSessionStore.getOrCreateSessionId(context);
                logger.debug("remove sessionId: {}", sessionId);
                store.remove(sessionId);

                destroy(context, newSessionStore);
            } else {
                logger.error("The session store should be able to build a new session store from the tracked session");
            }
        }
    }

    public Store<String, Object> getStore() {
        return store;
    }

    public void setStore(final Store<String, Object> store) {
        this.store = store;
    }

    public boolean isKillSession() {
        return killSession;
    }

    public void setKillSession(final boolean killSession) {
        this.killSession = killSession;
    }

    @Override
    public String toString() {
        return CommonHelper.toString(this.getClass(), "store", store, "killSession", killSession);
    }
}
