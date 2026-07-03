import {render, screen} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {FormErrorSummary} from './FormErrorSummary'

describe('FormErrorSummary', () => {
  it('renders nothing when there are no errors', () => {
    const {container} = render(<FormErrorSummary errors={[]} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('lists each error as an in-page anchor link', () => {
    render(
      <FormErrorSummary
        errors={[
          {id: 'clientId', label: 'Client ID', message: 'Required'},
          {id: 'scopes', label: 'Scopes', message: 'Pick at least one'},
        ]}
      />,
    )
    expect(screen.getByRole('alert')).toBeInTheDocument()
    const link = screen.getByRole('link', {name: /Client ID/i})
    expect(link).toHaveAttribute('href', '#clientId')
    expect(screen.getByText(/Pick at least one/i)).toBeInTheDocument()
  })

  it('renders a default title when errors are present', () => {
    render(<FormErrorSummary errors={[{id: 'x', label: 'X', message: 'Bad'}]} />)
    expect(screen.getByText(/please fix the following/i)).toBeInTheDocument()
  })

  it('accepts a custom title', () => {
    render(
      <FormErrorSummary
        title="Fix these issues:"
        errors={[{id: 'y', label: 'Y', message: 'Missing'}]}
      />,
    )
    expect(screen.getByText('Fix these issues:')).toBeInTheDocument()
  })
})
