import { createContext, useContext, type ReactNode } from 'react'
import { useToast, type UseToastReturn } from './use-toast'
import { Toast } from './Toast'
import styles from './Toast.module.css'

const ToastContext = createContext<UseToastReturn | null>(null)

export function useToastContext(): UseToastReturn {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToastContext must be used within a ToastProvider')
  return ctx
}

export interface ToastProviderProps {
  children: ReactNode
}

export function ToastProvider({ children }: ToastProviderProps) {
  const toastState = useToast()

  return (
    <ToastContext.Provider value={toastState}>
      {children}
      <div className={styles.container} role="region" aria-label="Notifications">
        {toastState.toasts.map((item) => (
          <Toast
            key={item.id}
            item={item}
            onDismiss={() => toastState.dismiss(item.id)}
            onPause={() => toastState.pauseTimer(item.id)}
            onResume={() => toastState.resumeTimer(item.id)}
          />
        ))}
      </div>
    </ToastContext.Provider>
  )
}
