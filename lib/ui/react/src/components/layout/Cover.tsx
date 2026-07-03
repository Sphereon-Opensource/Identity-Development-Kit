import type {CSSProperties, ReactNode} from 'react'
import type {SpaceKey} from './space'
import {spaceVar} from './space'
import styles from './layout.module.css'

export interface CoverProps {
  gap?: SpaceKey
  minHeight?: string
  className?: string
  children?: ReactNode
}

export function Cover({gap = '4', minHeight = '100%', className, children}: CoverProps) {
  return (
    <div
      className={[styles.cover, className].filter(Boolean).join(' ')}
      style={{'--cover-gap': spaceVar(gap), '--cover-min': minHeight} as CSSProperties}
    >
      {children}
    </div>
  )
}
