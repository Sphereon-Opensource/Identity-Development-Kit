import type {CSSProperties, ReactNode} from 'react'
import type {SpaceKey} from './space'
import {spaceVar} from './space'
import styles from './layout.module.css'

export interface GridProps {
  min?: string
  gap?: SpaceKey
  className?: string
  children?: ReactNode
}

export function Grid({min = '16rem', gap = '4', className, children}: GridProps) {
  return (
    <div
      className={[styles.grid, className].filter(Boolean).join(' ')}
      style={{'--grid-min': min, '--grid-gap': spaceVar(gap)} as CSSProperties}
    >
      {children}
    </div>
  )
}
