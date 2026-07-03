import type {CSSProperties, ReactElement} from 'react'
import styles from './Skeleton.module.css'

export interface SkeletonProps {
  width?: string
  height?: string
  radius?: string
  className?: string
}

/** Decorative shimmer placeholder. aria-hidden — announce loading via a sibling role="status". */
export function Skeleton({width = '100%', height = '1em', radius, className}: SkeletonProps): ReactElement {
  const style: CSSProperties = {width, height}
  if (radius) style.borderRadius = radius
  return <span aria-hidden="true" className={[styles.skeleton, className].filter(Boolean).join(' ')} style={style} />
}
