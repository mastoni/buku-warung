import { useEffect } from 'react';

/**
 * Lightweight scroll reveal hook using IntersectionObserver.
 * - Respects prefers-reduced-motion: reduce.
 * - Disconnects / unobserves after element is revealed to keep runtime overhead minimal.
 * - Triggers immediately for elements already in viewport.
 */
export function useScrollReveal() {
  useEffect(() => {
    if (typeof window === 'undefined') return;

    // Check if user prefers reduced motion
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReducedMotion) {
      // Immediately reveal all elements without transition
      const elements = document.querySelectorAll('.reveal-on-scroll');
      elements.forEach((el) => el.classList.add('revealed'));
      return;
    }

    const elements = document.querySelectorAll('.reveal-on-scroll');
    if (!elements.length) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('revealed');
            observer.unobserve(entry.target);
          }
        });
      },
      {
        root: null,
        rootMargin: '0px 0px -40px 0px',
        threshold: 0.08,
      }
    );

    elements.forEach((el) => observer.observe(el));

    return () => {
      observer.disconnect();
    };
  }, []);
}
