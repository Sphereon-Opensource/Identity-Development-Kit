import type {CSSProperties, ElementType, ReactNode} from 'react'
import type {SpaceKey} from './space'
import {spaceVar} from './space'
import styles from './layout.module.css'

export interface ClusterProps {
  as?: ElementType
  gap?: SpaceKey
  align?: string
  justify?: string
  className?: string
  children?: ReactNode
}

export function Cluster({as, gap = '3', align = 'flex-start', justify = 'flex-start', className, children}: ClusterProps) {
  const As = as ?? 'div'
  return (
    <As
      className={[styles.cluster, className].filter(Boolean).join(' ')}
      style={{
        '--cluster-gap': spaceVar(gap),
        '--cluster-align': align,
        '--cluster-justify': justify,
      } as CSSProperties}
    >
      {children}
    </As>
  )
}
