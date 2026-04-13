interface SessionTrackerProps {
  status: string | null
  stages: string[]
  error?: string | null
}

export function SessionTracker({ status, stages, error }: SessionTrackerProps) {
  if (!status) return null

  const currentIndex = stages.findIndex(s =>
    s.toLowerCase().replace(/[_ ]/g, '') === status?.toLowerCase().replace(/[_ ]/g, '')
  )

  return (
    <div className="session-tracker">
      <div className="stages">
        {stages.map((stage, i) => {
          const isCompleted = i < currentIndex || (i === currentIndex && i === stages.length - 1)
          const isCurrent = i === currentIndex && i < stages.length - 1
          return (
            <div key={stage} className={`stage ${isCompleted ? 'completed' : ''} ${isCurrent ? 'current' : ''}`}>
              <div className="stage-dot" />
              <span className="stage-label">{stage.replace(/_/g, ' ')}</span>
            </div>
          )
        })}
      </div>
      {error && <div className="error-message">{error}</div>}
    </div>
  )
}
