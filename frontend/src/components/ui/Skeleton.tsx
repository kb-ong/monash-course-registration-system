import type { ReactNode } from 'react'
import { cn } from '../../lib/cn'

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('skeleton-shimmer rounded-md', className)} aria-hidden="true" />
}

export function CourseCardSkeleton() {
  return (
    <div
      className="flex min-h-[232px] flex-col gap-3 rounded-lg border border-line-200 bg-surface p-6 shadow-e1"
      aria-hidden="true"
    >
      <div className="flex items-center justify-between">
        <Skeleton className="h-3 w-20" />
        <Skeleton className="h-[22px] w-12 rounded-pill" />
      </div>
      <Skeleton className="mt-2 h-5 w-3/4" />
      <Skeleton className="h-3.5 w-1/2" />
      <Skeleton className="mt-2 h-9 w-full" />
      <div className="mt-auto space-y-3">
        <Skeleton className="h-2 w-full rounded-pill" />
        <div className="flex items-center justify-between">
          <Skeleton className="h-10 w-28" />
          <Skeleton className="h-3 w-20" />
        </div>
      </div>
    </div>
  )
}

export function CourseGridSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="grid gap-4 md:grid-cols-2 md:gap-6 xl:grid-cols-3" aria-hidden="true">
      {Array.from({ length: count }).map((_, index) => (
        <CourseCardSkeleton key={index} />
      ))}
    </div>
  )
}

export function LineSkeleton({ children, className }: { children?: ReactNode; className?: string }) {
  return (
    <div className={cn('flex flex-col gap-3', className)} aria-hidden="true">
      {children ?? (
        <>
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="h-3 w-1/2" />
          <Skeleton className="h-3 w-5/6" />
        </>
      )}
    </div>
  )
}
