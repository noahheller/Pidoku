import { ArrowRight, CircleAlert, GraduationCap, Lightbulb, Pencil, SkipForward, Trophy } from 'lucide-react';
import { useCallback, useEffect, useMemo, useReducer, useState } from 'react';
import { recordEvent } from '../api/events';
import { gameReducer, isMistake, newGame, type GameAction } from '../game/game';
import { actionForKey } from '../game/keys';
import { markTutorialSeen } from '../game/storage';
import { highlightFor, isDone, lessons, nextStep, tutorialPuzzle, type Lesson } from '../tutorial/lessons';
import { Board, type BoardHighlight } from './Board';
import { Controls } from './Controls';
import { NumberPad } from './NumberPad';

interface TutorialProps {
  /** Called once the player finishes or skips it (it's then remembered as seen). */
  onDone: () => void;
  /** The button at the end, e.g. "Start the puzzle". */
  doneLabel: string;
}

/**
 * How to play: a short guided puzzle on a nearly full board, shown the first time someone opens a
 * puzzle and from "How to play" on the home screen. Each step points at cells and says why, worded
 * by the engine (see tutorial/lessons.ts); along the way it covers notes, erase, undo and mistakes.
 * Not timed and not recorded as an attempt.
 */
export function Tutorial({ onDone, doneLabel }: TutorialProps) {
  const [state, dispatch] = useReducer(gameReducer, tutorialPuzzle, (p) => newGame(p));
  const [index, setIndex] = useState(0);
  const [shown, setShown] = useState<BoardHighlight | null>(null);
  const [hint, setHint] = useState<string | null>(null);
  const lesson: Lesson | undefined = lessons[index];
  const finished = index >= lessons.length;

  const finish = useCallback(
    (outcome: 'completed' | 'skipped') => {
      markTutorialSeen();
      if (outcome === 'completed') recordEvent('tutorial_completed', { detail: { mistakes: state.mistakes } });
      else recordEvent('tutorial_skipped', { detail: { step: index } });
      onDone();
    },
    [index, state.mistakes, onDone],
  );

  // A task done moves on by itself (set while rendering, so the next lesson shows in the same paint).
  if (lesson !== undefined && isDone(lesson, state)) {
    setIndex(index + 1);
    setShown(null);
    setHint(null);
  }

  const play = useCallback((action: GameAction) => {
    if (action.type === 'input' || action.type === 'erase') setShown(null);
    dispatch(action);
  }, []);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) return;
      const action = actionForKey(event, state.rules);
      if (action && action.type !== 'restart') {
        event.preventDefault();
        play(action);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [state.rules, play]);

  const highlight = useMemo((): BoardHighlight | null => {
    if (shown) return shown;
    if (!lesson) return null;
    switch (lesson.kind) {
      case 'info':
      case 'notes':
        return lesson.highlight;
      case 'place':
        return highlightFor(lesson.step);
      case 'finish':
        return null;
    }
  }, [lesson, shown]);

  const wrong = state.values.some((_, i) => isMistake(state, i));
  const writingNotes = state.notesMode && lesson?.kind === 'place';

  return (
    <main className="mx-auto grid max-w-[1040px] gap-3 px-2 pt-2 pb-6 sm:gap-4 sm:px-4 sm:pt-4 sm:pb-8">
      <header className="flex flex-wrap items-center gap-3">
        <h1 className="flex flex-1 items-center gap-2 text-xl font-bold sm:text-2xl">
          <GraduationCap className="size-6 text-primary" aria-hidden="true" /> How to play
        </h1>
        {!finished && (
          <>
            <span className="text-sm text-base-content/60 tabular-nums" aria-label={`Step ${index + 1} of ${lessons.length}`}>
              {index + 1} / {lessons.length}
            </span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => finish('skipped')}>
              <SkipForward className="size-4" aria-hidden="true" /> Skip
            </button>
          </>
        )}
      </header>

      <section className="card border border-base-300 bg-base-100 shadow-sm" aria-live="polite" aria-labelledby="lesson-title">
        <div className="card-body gap-2 p-4 sm:p-5">
          {finished || !lesson ? (
            <>
              <h2 id="lesson-title" className="flex items-center gap-1.5 text-lg font-bold">
                <Trophy className="size-5 text-success" aria-hidden="true" /> That's Pidoku
              </h2>
              <p>
                You solved it{state.mistakes > 0 ? ` with ${state.mistakes} mistake${state.mistakes === 1 ? '' : 's'}` : ' without a mistake'}. Every puzzle is the same idea, with more to work out:
                look for a row, column or box that needs a digit, and count the copies it still needs.
              </p>
              <button type="button" className="btn btn-primary mt-1 sm:w-fit" onClick={() => finish('completed')} autoFocus>
                {doneLabel} <ArrowRight className="size-4" aria-hidden="true" />
              </button>
            </>
          ) : (
            <>
              <h2 id="lesson-title" className="text-lg font-bold">
                {lesson.title}
              </h2>
              <p>{lesson.body}</p>
              {lesson.kind === 'place' && <p className="text-sm text-base-content/70">{lesson.instruction}</p>}
              {hint && <p className="flex items-start gap-1.5 text-sm"><Lightbulb className="mt-0.5 size-4 shrink-0 text-warning" aria-hidden="true" /> {hint}</p>}
              {wrong && (
                <p role="alert" className="flex items-start gap-1.5 text-sm text-error">
                  <CircleAlert className="mt-0.5 size-4 shrink-0" aria-hidden="true" /> That's not it: a wrong digit shows in red and counts as a mistake. Tap Erase or Undo, then try again.
                </p>
              )}
              {writingNotes && !wrong && (
                <p className="flex items-start gap-1.5 text-sm text-secondary">
                  <Pencil className="mt-0.5 size-4 shrink-0" aria-hidden="true" /> Notes is on, so digits go in as notes. Turn it off to write one.
                </p>
              )}
              <div className="mt-1 flex flex-wrap gap-2">
                {lesson.kind === 'info' && (
                  <button type="button" className="btn btn-primary btn-sm" onClick={() => setIndex((i) => i + 1)} autoFocus>
                    Next <ArrowRight className="size-4" aria-hidden="true" />
                  </button>
                )}
                {lesson.kind === 'finish' && (
                  <button
                    type="button"
                    className="btn btn-sm"
                    onClick={() => {
                      const step = nextStep(state.values);
                      if (!step) return;
                      setShown(highlightFor(step));
                      setHint(step.text);
                    }}
                  >
                    <Lightbulb className="size-4" aria-hidden="true" /> Show me
                  </button>
                )}
              </div>
            </>
          )}
        </div>
      </section>

      <div className="flex flex-wrap items-start justify-center gap-6">
        <Board state={state} onSelect={(i) => dispatch({ type: 'select', index: i })} highlight={finished ? null : highlight} />
        <div className="grid w-full gap-4 sm:w-[min(100%,360px)]">
          <NumberPad state={state} dispatch={play} />
          <Controls state={state} dispatch={play} restart={false} />
        </div>
      </div>
    </main>
  );
}
