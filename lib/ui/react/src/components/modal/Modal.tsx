import {type ReactNode} from 'react'
import {createPortal} from 'react-dom'
import {useModal, type UseModalProps} from './use-modal'
import styles from './Modal.module.css'

export interface ModalProps extends UseModalProps {
  children: ReactNode
  title?: ReactNode
  description?: ReactNode
  className?: string
}

/**
 * Styled modal dialog rendered via portal with focus trap and scroll lock.
 *
 * @example
 * ```tsx
 * <Modal isOpen={isOpen} onClose={() => setOpen(false)} title="Confirm">
 *   <p>Are you sure?</p>
 *   <Button onClick={() => setOpen(false)}>Close</Button>
 * </Modal>
 * ```
 */
export function Modal({
  children,
  title,
  description,
  className,
  ...props
}: ModalProps) {
  const { overlayProps, dialogProps, dialogRef, titleProps, descriptionProps } = useModal(props)

  if (!props.isOpen) return null

  // Build ARIA attributes conditionally
  const ariaLabelledBy = title ? titleProps.id : undefined
  const ariaDescribedBy = description ? descriptionProps.id : undefined

  const content = (
    <div className={styles.overlay} {...overlayProps}>
      <div
        className={`${styles.dialog} ${className ?? ''}`.trim()}
        {...dialogProps}
        ref={dialogRef}
        aria-labelledby={ariaLabelledBy}
        aria-describedby={ariaDescribedBy}
      >
        {title ? <h2 {...titleProps} className={styles.title}>{title}</h2> : null}
        {description ? <p {...descriptionProps} className={styles.description}>{description}</p> : null}
        <div className={styles.content}>{children}</div>
      </div>
    </div>
  )

  // Render via portal to escape DOM hierarchy issues
  if (typeof document !== 'undefined') {
    return createPortal(content, document.body)
  }
  return content
}
