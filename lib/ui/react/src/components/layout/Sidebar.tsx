import type {CSSProperties, ReactNode} from 'react'
import type {SpaceKey} from './space'
import {spaceVar} from './space'
import styles from './layout.module.css'

export interface SidebarProps {
  sideWidth?: string
  contentMin?: string
  gap?: SpaceKey
  className?: string
  children?: ReactNode
}

export function Sidebar({sideWidth = '16rem', contentMin = '60%', gap = '4', className, children}: SidebarProps) {
  return (
    <div
      className={[styles.sidebar, className].filter(Boolean).join(' ')}
      style={{'--sidebar-width': sideWidth, '--sidebar-content-min': contentMin, '--sidebar-gap': spaceVar(gap)} as CSSProperties}
    >
      {children}
    </div>
  )
}
