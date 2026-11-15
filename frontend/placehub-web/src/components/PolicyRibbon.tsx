import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { X } from 'lucide-react';
import { applicationApi } from '../api/endpoints';
import type { PlacementStateView } from '../types';
import { formatDate } from '../utils/format';
import { shouldShowRibbon } from '../utils/policy';
import { readFlag, writeFlag } from '../utils/theme';
import styles from './PolicyRibbon.module.css';

const KEY = 'placehub.policyRibbon.dismissed';

export function PolicyRibbon() {
  const [state, setState] = useState<PlacementStateView | null>(null);
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    let active = true;
    applicationApi.placementState().then((s) => active && setState(s)).catch(() => undefined);
    return () => { active = false; };
  }, []);

  if (hidden || !shouldShowRibbon(state, Date.now(), readFlag(KEY))) return null;
  return (
    <div className={styles.ribbon} role="region" aria-label="Placement policy update">
      <span>
        Placement policy updated on {formatDate(state!.policyEffectiveFrom)}.{' '}
        <Link to="/student#placement-state">Review it</Link>
      </span>
      <button type="button" className={styles.close} aria-label="Dismiss policy update notice"
        onClick={() => { writeFlag(KEY, String(state!.policyVersion)); setHidden(true); }}>
        <X aria-hidden="true" />
      </button>
    </div>
  );
}
