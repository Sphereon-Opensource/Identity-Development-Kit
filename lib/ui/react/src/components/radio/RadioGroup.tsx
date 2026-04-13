import { useEffect, useRef, type ReactNode } from 'react'
import { useRadioGroup, RadioGroupContext, type UseRadioGroupProps, type RadioGroupContextValue } from './use-radio'

export interface RadioGroupProps extends UseRadioGroupProps {
  children: ReactNode
  className?: string
}

export function RadioGroup({ children, className, ...props }: RadioGroupProps) {
  const { groupProps, getRadioProps, selectedValue } = useRadioGroup(props)
  const valuesRef = useRef<string[]>([])

  const contextValue: RadioGroupContextValue = {
    name: (groupProps as any)['aria-label'] ?? '',
    selectedValue,
    onSelect: (value: string) => {
      // Trigger the onChange through getRadioProps
      const radioProps = getRadioProps(value)
      radioProps.onChange()
    },
    orientation: props.orientation ?? 'vertical',
    registerValue: (v: string) => {
      if (!valuesRef.current.includes(v)) valuesRef.current.push(v)
    },
    unregisterValue: (v: string) => {
      valuesRef.current = valuesRef.current.filter((x) => x !== v)
    },
    values: valuesRef.current,
  }

  return (
    <RadioGroupContext.Provider value={contextValue}>
      <div {...groupProps} className={className}>
        {children}
      </div>
    </RadioGroupContext.Provider>
  )
}
