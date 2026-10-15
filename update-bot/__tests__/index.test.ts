/**
 * Unit tests for the action's entrypoint, src/index.ts
 */

import { jest } from '@jest/globals'

// Mock the action's entrypoint
const runMock = jest.fn<() => Promise<void>>()
jest.unstable_mockModule('../src/main.js', () => ({ run: runMock }))

describe('index', () => {
  it('calls run when imported', async () => {
    await import('../src/index.js')

    expect(runMock).toHaveBeenCalled()
  })
})
