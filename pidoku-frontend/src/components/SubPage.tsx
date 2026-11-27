import { ArrowLeft, CircleAlert, RotateCw, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

/** A page off the home screen or the You page: a back link, its title, and what it's for. */
export function SubPage({ back, icon: Icon, title, children }: { back: { to: string; label: string }; icon: LucideIcon; title: string; children: ReactNode }) {
  return (
    <main className="mx-auto grid max-w-xl gap-5 px-4 pt-4 pb-16">
      <header className="grid gap-3">
        <Link to={back.to} className="back-link btn btn-ghost btn-sm w-fit text-primary">
          <ArrowLeft className="size-4 shrink-0" aria-hidden="true" /> {back.label}
        </Link>
        <h1 className="flex items-center gap-2 text-2xl font-bold">
          <Icon className="size-6 text-primary" aria-hidden="true" /> {title}
        </h1>
      </header>
      {children}
    </main>
  );
}

export function Card({ title, tone, children }: { title?: ReactNode; tone?: 'danger'; children: ReactNode }) {
  return (
    <section className={`card border bg-base-100 shadow-sm ${tone === 'danger' ? 'border-error/30' : 'border-base-300'}`}>
      <div className="card-body gap-4 p-4 sm:p-5">
        {title && <h2 className="flex items-center gap-2 text-lg font-semibold">{title}</h2>}
        {children}
      </div>
    </section>
  );
}

export function Loading() {
  return (
    <p className="flex items-center gap-2 text-base-content/60" role="status">
      <span className="loading loading-spinner loading-sm" aria-hidden="true" /> Loading…
    </p>
  );
}

export function Failed({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div role="alert" className="alert alert-soft alert-warning flex flex-wrap">
      <CircleAlert className="size-5" aria-hidden="true" />
      <span>{message}</span>
      <button type="button" className="btn btn-plain btn-sm" onClick={onRetry}>
        <RotateCw className="size-4" aria-hidden="true" /> Retry
      </button>
    </div>
  );
}

/** The spinner while a page's data loads, or what went wrong. Nothing once it's loaded. */
export function LoadStatus({ state, onRetry }: { state: { status: string; message?: string }; onRetry: () => void }) {
  if (state.status === 'loading') return <Loading />;
  if (state.status === 'failed') return <Failed message={state.message ?? 'Something went wrong.'} onRetry={onRetry} />;
  return null;
}
