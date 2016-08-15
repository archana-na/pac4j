package org.pac4j.core.context.session;

import org.pac4j.core.context.MockWebContext;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Mock a session store in memory.
 *
 * @author Jerome Leleu
 * @since 1.9.0
 */
public class MockSessionStore implements SessionStore<MockWebContext> {

    protected Map<String, Object> store = new HashMap<>();

    protected String id;

    public MockSessionStore() {}

    public MockSessionStore(final Map<String, Object> store) {
        this.store = store;
    }

    @Override
    public String getOrCreateSessionId(final MockWebContext context) {
        if (id == null) {
            id = new Date().toString();
        }
        return id;
    }

    @Override
    public Object get(final MockWebContext context, final String key) {
        return store.get(key);
    }

    @Override
    public void set(final MockWebContext context, final String key, final Object value) {
        store.put(key, value);
    }

    @Override
    public void invalidateSession(final MockWebContext context) {
        store.clear();

        id = null;
    }

    @Override
    public Optional<Object> getTrackableSession(final MockWebContext context) {
        return Optional.of(store);
    }

    @Override
    public Optional<SessionStore<MockWebContext>> buildFromTrackableSession(final MockWebContext context, final Object trackableSession) {
        return Optional.of(new MockSessionStore((Map<String, Object>) trackableSession));
    }

    @Override
    public boolean renew(final MockWebContext context) {
        id = null;
        return true;
    }
}
