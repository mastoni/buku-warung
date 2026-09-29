import { useCallback, useEffect, useState } from 'react';
import { TEST_CAMPAIGN_CONFIG } from '../data/landingData';
import { getCurrentLeadToken, trackTestProgramFull } from '../tracking';

export type TestCampaignStatus = 'OPEN' | 'FULL';

export interface TestCampaignState {
  campaignId: string;
  status: TestCampaignStatus;
  capacity: number;
  registered: number;
  remaining: number;
}

export interface TestRegistrationResult extends TestCampaignState {
  slotNumber?: number;
  alreadyRegistered?: boolean;
}

type SubmitState = 'idle' | 'submitting' | 'success' | 'full' | 'error';

const INITIAL_STATE: TestCampaignState = {
  campaignId: TEST_CAMPAIGN_CONFIG.campaignId,
  status: 'OPEN',
  // Deliberately shows the true capacity as unknown until the server answers. Nothing on this
  // page may claim an availability number it has not been told by the server.
  capacity: TEST_CAMPAIGN_CONFIG.capacity,
  registered: 0,
  remaining: TEST_CAMPAIGN_CONFIG.capacity
};

/**
 * Server-authoritative closed-testing campaign state.
 *
 * The "X dari 50 slot" figure rendered by the page is always the value the server returned.
 * There is no local counter and no localStorage cache of availability: on a full page reload
 * the page re-asks the server, so a stale "42 remaining" can never be displayed. localStorage
 * is used only to remember that this device already registered, which is a UX nicety and never
 * a source of truth for capacity.
 */
export const useTestCampaign = () => {
  const [state, setState] = useState<TestCampaignState>(INITIAL_STATE);
  const [loading, setLoading] = useState(true);
  const [submitState, setSubmitState] = useState<SubmitState>('idle');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [result, setResult] = useState<TestRegistrationResult | null>(null);
  const [alreadyRegistered, setAlreadyRegistered] = useState<boolean>(() => {
    try {
      return window.localStorage.getItem('lw_test_registered') === '1';
    } catch {
      return false;
    }
  });

  const applyServerState = useCallback((next: TestCampaignState) => {
    setState(next);
    if (next.status === 'FULL') {
      trackTestProgramFull();
    }
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const response = await fetch(TEST_CAMPAIGN_CONFIG.statusEndpoint, {
        method: 'GET',
        headers: { Accept: 'application/json' }
      });
      if (!response.ok) throw new Error(`status ${response.status}`);
      const payload = (await response.json()) as { success: boolean; data: TestCampaignState };
      if (payload.success && payload.data) {
        applyServerState(payload.data);
      }
    } catch {
      // Availability that cannot be confirmed is never invented. Keep the neutral initial
      // state and let the section render as "checking" rather than a fabricated number.
    } finally {
      setLoading(false);
    }
  }, [applyServerState]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const register = useCallback(
    async (details: {
      name: string;
      whatsapp: string;
      googlePlayEmail: string;
      businessType: string;
      dailyTransactions: string;
      androidDevice: string;
      consent: boolean;
    }): Promise<boolean> => {
      setSubmitState('submitting');
      setErrorMessage(null);
      try {
        const response = await fetch(TEST_CAMPAIGN_CONFIG.registerEndpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ ...details, leadToken: getCurrentLeadToken() })
        });

        const payload = (await response.json()) as {
          success: boolean;
          code?: string;
          error?: { message?: string };
          data?: TestCampaignState & { slotNumber?: number; alreadyRegistered?: boolean };
        };

        if (response.status === 409 || payload.code === 'CAMPAIGN_FULL') {
          if (payload.data) applyServerState(payload.data);
          setSubmitState('full');
          return false;
        }

        if (!response.ok || !payload.success || !payload.data) {
          setErrorMessage(payload.error?.message || 'Pendaftaran gagal. Silakan coba lagi.');
          setSubmitState('error');
          return false;
        }

        const { slotNumber, alreadyRegistered: dup, ...serverState } = payload.data;
        applyServerState(serverState);
        setResult({ ...serverState, slotNumber, alreadyRegistered: dup });
        setAlreadyRegistered(true);
        try {
          window.localStorage.setItem('lw_test_registered', '1');
        } catch {
          // Private-mode browsers may refuse storage; the server state still governs.
        }
        setSubmitState('success');
        return true;
      } catch {
        setErrorMessage('Tidak dapat terhubung ke server. Silakan coba lagi.');
        setSubmitState('error');
        return false;
      }
    },
    [applyServerState]
  );

  return {
    state,
    loading,
    submitState,
    errorMessage,
    result,
    alreadyRegistered,
    isFull: state.status === 'FULL',
    isOpen: state.status === 'OPEN' && !loading,
    register,
    refresh
  };
};
