import { describe, expect, it, vi } from 'vitest'
import { authorizeNavigation } from './index'

function createStore(overrides = {}) {
  return {
    authReady: false,
    authenticated: false,
    user: null,
    restoreSession: vi.fn(),
    bootstrapWorkspace: vi.fn(),
    clearAuth: vi.fn(),
    ...overrides
  }
}

describe('authorizeNavigation', () => {
  it('does not probe the protected session on the anonymous login page', async () => {
    const store = createStore()

    await expect(authorizeNavigation({ meta: { public: true } }, store)).resolves.toBe(true)
    expect(store.restoreSession).not.toHaveBeenCalled()
  })

  it('restores the session before entering a protected route', async () => {
    const store = createStore()
    store.restoreSession.mockImplementation(async () => {
      store.authReady = true
      store.authenticated = true
      store.user = { roles: ['ANALYST'] }
    })

    await expect(authorizeNavigation({ meta: {}, fullPath: '/chat' }, store)).resolves.toBe(true)
    expect(store.restoreSession).toHaveBeenCalledOnce()
    expect(store.bootstrapWorkspace).toHaveBeenCalledOnce()
  })

  it('redirects anonymous users away from protected routes', async () => {
    const store = createStore({ authReady: true })

    await expect(authorizeNavigation({ meta: {}, fullPath: '/admin' }, store)).resolves.toEqual({
      name: 'login',
      query: { redirect: '/admin' }
    })
  })

  it('enforces role metadata after authentication', async () => {
    const store = createStore({
      authReady: true,
      authenticated: true,
      user: { roles: ['ANALYST'] }
    })

    await expect(
      authorizeNavigation({ meta: { roles: ['ADMIN'] }, fullPath: '/admin' }, store)
    ).resolves.toBe('/chat')
  })
})
