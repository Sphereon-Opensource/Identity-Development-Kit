import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, it, vi} from 'vitest'
import {Modal} from './Modal'

describe('Modal', () => {
  it('does not render when closed', () => {
    render(<Modal isOpen={false} onClose={() => {}}>Content</Modal>)
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('renders when open', () => {
    render(<Modal isOpen={true} onClose={() => {}}>Content</Modal>)
    expect(screen.getByRole('dialog')).toBeDefined()
  })

  it('renders children content', () => {
    render(<Modal isOpen={true} onClose={() => {}}>Modal body</Modal>)
    expect(screen.getByText('Modal body')).toBeDefined()
  })

  it('renders title', () => {
    render(
      <Modal isOpen={true} onClose={() => {}} title="My Title">
        Content
      </Modal>,
    )
    expect(screen.getByText('My Title')).toBeDefined()
  })

  it('renders description', () => {
    render(
      <Modal isOpen={true} onClose={() => {}} description="My Desc">
        Content
      </Modal>,
    )
    expect(screen.getByText('My Desc')).toBeDefined()
  })

  it('sets aria-modal', () => {
    render(<Modal isOpen={true} onClose={() => {}}>Content</Modal>)
    expect(screen.getByRole('dialog').getAttribute('aria-modal')).toBe('true')
  })

  it('sets aria-labelledby when title present', () => {
    render(<Modal isOpen={true} onClose={() => {}} title="Title">Content</Modal>)
    const dialog = screen.getByRole('dialog')
    const labelledBy = dialog.getAttribute('aria-labelledby')
    expect(labelledBy).toBeTruthy()
    expect(document.getElementById(labelledBy!)).not.toBeNull()
  })

  it('sets aria-describedby', () => {
    render(<Modal isOpen={true} onClose={() => {}} description="Desc">Content</Modal>)
    const dialog = screen.getByRole('dialog')
    const describedBy = dialog.getAttribute('aria-describedby')
    expect(describedBy).toBeTruthy()
    expect(document.getElementById(describedBy!)).not.toBeNull()
  })

  it('calls onClose on Escape', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(<Modal isOpen={true} onClose={onClose}>Content</Modal>)
    await user.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('does not call onClose on Escape when closeOnEscape=false', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(<Modal isOpen={true} onClose={onClose} closeOnEscape={false}>Content</Modal>)
    await user.keyboard('{Escape}')
    expect(onClose).not.toHaveBeenCalled()
  })

  it('calls onClose on overlay click', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(<Modal isOpen={true} onClose={onClose}>Content</Modal>)
    // The overlay is the parent of the dialog, rendered via portal to document.body
    const dialog = screen.getByRole('dialog')
    const overlay = dialog.parentElement as HTMLElement
    await user.click(overlay)
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('does not call onClose when clicking inside dialog', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(<Modal isOpen={true} onClose={onClose}>Content</Modal>)
    await user.click(screen.getByText('Content'))
    expect(onClose).not.toHaveBeenCalled()
  })

  it('does not call onClose on overlay click when closeOnOverlayClick=false', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(
      <Modal isOpen={true} onClose={onClose} closeOnOverlayClick={false}>Content</Modal>,
    )
    const dialog = screen.getByRole('dialog')
    const overlay = dialog.parentElement as HTMLElement
    await user.click(overlay)
    expect(onClose).not.toHaveBeenCalled()
  })

  it('prevents body scroll when open', () => {
    render(<Modal isOpen={true} onClose={() => {}}>Content</Modal>)
    expect(document.body.style.overflow).toBe('hidden')
  })
})
