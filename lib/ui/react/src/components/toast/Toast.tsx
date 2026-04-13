import type { ToastItem } from './use-toast'
import styles from './Toast.module.css'

interface ToastInternalProps {
  item: ToastItem
  onDismiss: () => void
  onPause: () => void
  onResume: () => void
}

export function Toast({ item, onDismiss, onPause, onResume }: ToastInternalProps) {
  const role = item.variant === 'error' ? 'alert' : 'status'
  return (
    <div
      className={`${styles.toast} ${styles[item.variant ?? 'default']}`}
      role={role}
      aria-atomic="true"
      onMouseEnter={onPause}
      onMouseLeave={onResume}
      onFocus={onPause}
      onBlur={onResume}
    >
      <span className={styles.message}>{item.message}</span>
      {item.dismissible ? (
        <button
          className={styles.dismiss}
          onClick={onDismiss}
          aria-label="Dismiss notification"
          type="button"
        >
          ×
        </button>
      ) : null}
    </div>
  )
}
