import {type HTMLAttributes, type ReactNode} from 'react'
import styles from './Tabs.module.css'

export interface TabsProps {
  children: ReactNode
  className?: string
}

export interface TabListProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
}

export interface TabProps {
  children: ReactNode
  index: number
  className?: string
  tabProps?: HTMLAttributes<HTMLButtonElement>
}

export interface TabPanelProps {
  children: ReactNode
  index: number
  className?: string
  panelProps?: HTMLAttributes<HTMLDivElement>
}

export function Tabs({ children, className }: TabsProps) {
  return <div className={`${styles.tabs} ${className ?? ''}`.trim()}>{children}</div>
}

export function TabList({ children, ...rest }: TabListProps) {
  return (
    <div {...rest} className={styles.tabList}>
      {children}
    </div>
  )
}

export function Tab({ children, index, className, tabProps }: TabProps) {
  return (
    <button {...tabProps} className={`${styles.tab} ${className ?? ''}`.trim()} type="button">
      {children}
    </button>
  )
}

export function TabPanel({ children, index, className, panelProps }: TabPanelProps) {
  return (
    <div {...panelProps} className={`${styles.panel} ${className ?? ''}`.trim()}>
      {children}
    </div>
  )
}
