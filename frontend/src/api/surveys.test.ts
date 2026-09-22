import { describe, expect, it } from 'vitest'
import { emptyQuestion } from './surveys'

describe('emptyQuestion', () => {
  it('creates scale defaults', () => {
    const q = emptyQuestion('SCALE')
    expect(q.scaleMin).toBe(1)
    expect(q.scaleMax).toBe(5)
    expect(q.options).toHaveLength(0)
  })
  it('creates two options for choice questions', () => {
    expect(emptyQuestion('SINGLE_CHOICE').options).toHaveLength(2)
    expect(emptyQuestion('MULTIPLE_CHOICE').options).toHaveLength(2)
  })
})
