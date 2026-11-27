import { ArrowLeft, ShieldCheck } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

const CONTACT = 'pidoku@googlegroups.com';
const UPDATED = '26 November 2026';

/** `/privacy`: what Pidoku keeps about players, who sees it, and how to delete it. */
export function PrivacyPage() {
  return (
    <main className="mx-auto grid max-w-xl gap-6 px-4 pt-4 pb-16">
      <header className="grid gap-3">
        <Link to="/" className="back-link btn btn-ghost btn-sm w-fit text-primary">
          <ArrowLeft className="size-4 shrink-0" aria-hidden="true" /> Puzzles
        </Link>
        <h1 className="flex items-center gap-2 text-2xl font-bold">
          <ShieldCheck className="size-6 text-primary" aria-hidden="true" /> Privacy
        </h1>
        <p className="text-base-content/80">
          Pidoku is a free puzzle game. Made for my amazing girlfriend. It keeps only what it needs for streaks, friends and leaderboards, and a few game events to learn what to improve. There are no
          ads, no third-party analytics and no tracking cookies, and nothing is sold or shared for marketing.
        </p>
        <p className="text-sm text-base-content/60">Last updated {UPDATED}.</p>
      </header>

      <Section title="What Pidoku keeps">
        <Item name="An account ID">
          Every player gets an account without signing up: a random ID from Firebase (Google's sign-in service). It's how the game knows you're the same player next time.
        </Item>
        <Item name="Your Google email, if you sign in with Google">
          Firebase keeps your Google account's email address and name so you can sign in on other devices. Pidoku's own database doesn't store your email; it's only shown to you,
          on your own page. If you haven't picked a display name, Pidoku uses your Google account's name as one, which you can change any time.
        </Item>
        <Item name="The name you pick">Optional. Until you pick one you show as Anonymous.</Item>
        <Item name="Your friends">Your friend code and who you're friends with.</Item>
        <Item name="Your solves">
          Which puzzles you start and finish, when, how long they took, how many mistakes you made and how many hints you used. These make your streak and the leaderboards.
        </Item>
        <Item name="Game events">
          A few things that happen in the game, tied to your account ID: tapping Share or Copy on a result, opening a share link (and whether you were new), leaving a puzzle
          unfinished (how far you got), and finishing or skipping the tutorial. They're counted to see which puzzles are too hard and whether sharing brings new players. They're
          kept in Pidoku's own database, not sent to any analytics company.
        </Item>
        <Item name="Ko-fi tips, if you support Pidoku">
          If you tip on Ko-fi with your supporter code, Pidoku keeps the date you first supported it and Ko-fi's transaction number for the tip, so the same tip isn't counted twice.
          Ko-fi handles the payment; Pidoku doesn't keep your name, email, message or the amount from Ko-fi. Your code is deleted once it's used, or after 30 days.
        </Item>
        <Item name="Games in progress">
          Once you've started a puzzle, its board is saved to your account every few seconds, so you can carry on on another device. It's cleared when you finish.
        </Item>
        <Item name="Request logs">
          Like most websites, the hosting (Firebase Hosting and Google Cloud Run) briefly logs requests, including IP addresses and browser type, to keep the site working and secure.
        </Item>
      </Section>

      <Section title="What stays in your browser">
        <p>
          Games in progress, your results, your recent puzzles and settings like the leaderboard filters are saved in this browser's storage. They stay on your device, apart from
          solves and games in progress on Pidoku's puzzles, which are sent to the server as above.
        </p>
      </Section>

      <Section title="Who can see it">
        <ul className="list-disc ps-5">
          <li>Everyone: the leaderboards show your name (or Anonymous) and your times on the daily puzzles, and a supporter badge if you've tipped on Ko-fi.</li>
          <li>
            Your profile (your streak, best times and recent dailies): your friends by default. Under <strong>You › Account &amp; privacy</strong> you can make it public, so any player can see
            it, or private, so only you can. Only players signed in with Google can see other players' profiles.
          </li>
          <li>
            Your puzzle history (every puzzle you've solved, random ones included, with your times) is shown on your profile: to your friends by default. It has its own setting under{' '}
            <strong>You › Account &amp; privacy</strong>, and is never seen by more people than your profile is. Next to each puzzle, the person looking sees their own time and their friends'
            times on it, from those whose history they're allowed to see.
          </li>
          <li>Nobody else sees your email, and no one but you sees your unfinished games.</li>
        </ul>
        <p>
          The services that run Pidoku handle the data for it and nothing else: Google Firebase (sign-in and hosting), Google Cloud (the game server) and Neon (the database). If you tip, Ko-fi handles the payment under its own privacy policy.
        </p>
      </Section>

      <Section title="Deleting your data">
        <p>
          Under{' '}
          <Link to="/me/account" className="link link-primary">
            You › Account &amp; privacy
          </Link>
          , choose <strong>Delete my account</strong>. It deletes straight away and for good your name, friends, every solve and game in progress (so your times leave the leaderboards), your
          game events, your supporter code and tip records, your sign-in, and
          Pidoku's data in that browser. Database backups can hold a copy for a short while until they expire.
        </p>
        <p>
          You can remove a friend on the Friends page, and change your name on the You page, any time. Clearing your browser's data for this site deletes what's kept in the browser; if you never signed in with
          Google, that also loses your account.
        </p>
        <p>
          Can't get to your account any more, or have a question? Email{' '}
          <a href={`mailto:${CONTACT}`} className="link link-primary">
            {CONTACT}
          </a>{' '}
          and we'll help, including deleting your data for you.
        </p>
      </Section>

      <Section title="Children">
        <p>Pidoku isn't aimed at children under 13 and doesn't knowingly collect their data. If you think a child has an account, email us and we'll delete it.</p>
      </Section>

      <Section title="Changes">
        <p>If what Pidoku keeps changes, this page will say so and the date at the top will change.</p>
      </Section>
    </main>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="grid gap-2 text-base-content/80">
      <h2 className="text-lg font-semibold text-base-content">{title}</h2>
      {children}
    </section>
  );
}

function Item({ name, children }: { name: string; children: ReactNode }) {
  return (
    <div>
      <h3 className="font-semibold text-base-content">{name}</h3>
      <p>{children}</p>
    </div>
  );
}
