import type {ReactElement} from 'react'
import styles from './SkipLink.module.css'

export interface SkipLinkProps {
  /** id of the element to jump to (default "main-content"). */
  targetId?: string
  /** visible/announced label (default "Skip to main content"). */
  label?: string
}

/** Visually hidden until focused; first focusable element in the shell so keyboard
 *  users can bypass the top bar and navigation. */
export function SkipLink({targetId = 'main-content', label = 'Skip to main content'}: SkipLinkProps): ReactElement {
  return (
    <a className={styles.skipLink} href={`#${targetId}`}>
      {label}
    </a>
  )
}
