import type {CSSProperties, ElementType, ReactNode} from 'react'
import type {SpaceKey} from './space'
import {spaceVar} from './space'
import styles from './layout.module.css'

export interface BoxProps {
  as?: ElementType
  padding?: SpaceKey
  className?: string
  children?: ReactNode
}

export function Box({as, padding, className, children}: BoxProps) {
  const As = as ?? 'div'
  const style = padding ? ({'--box-padding': spaceVar(padding)} as CSSProperties) : undefined
  return (
    <As className={[styles.box, className].filter(Boolean).join(' ')} style={style}>
      {children}
    </As>
  )
}
