import { Globe, Lock, Users, type LucideIcon } from 'lucide-react';
import type { ProfileVisibility } from '../api/client';

/** How each profile visibility is named and explained, in the order the setting lists them. */
export const VISIBILITY: Record<ProfileVisibility, { label: string; description: string; Icon: LucideIcon }> = {
  public: { label: 'Public', description: 'Any player signed in with Google can see it, e.g. from a leaderboard.', Icon: Globe },
  friends: { label: 'Friends only', description: 'Only your friends signed in with Google can see it.', Icon: Users },
  private: { label: 'Private', description: 'Only you can see it.', Icon: Lock },
};
