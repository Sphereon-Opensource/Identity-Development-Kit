import type {CSSProperties, ElementType, ReactNode} from 'react'
import type {SpaceKey} from './space'
import {spaceVar} from './space'
import styles from './layout.module.css'

export interface StackProps {
  as?: ElementType
  gap?: SpaceKey
  className?: string
  children?: ReactNode
}

export function Stack({as, gap = '4', className, children}: StackProps) {
  const As = as ?? 'div'
  return (
    <As
      className={[styles.stack, className].filter(Boolean).join(' ')}
      style={{'--stack-gap': spaceVar(gap)} as CSSProperties}
    >
      {children}
    </As>
  )
}
