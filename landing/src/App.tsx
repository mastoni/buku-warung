import React, { useEffect } from 'react';
import { Navbar } from './components/Navbar';
import { Hero } from './components/Hero';
import { ProblemSolution } from './components/ProblemSolution';
import { ScreenshotShowcase } from './components/ScreenshotShowcase';
import { WhatYouGet } from './components/WhatYouGet';
import { PricingSection } from './components/PricingSection';
import { HowToBuy } from './components/HowToBuy';
import { TrustSection } from './components/TrustSection';
import { FaqSection } from './components/FaqSection';
import { FinalCta } from './components/FinalCta';
import { Footer } from './components/Footer';
import { StickyMobileCta } from './components/StickyMobileCta';
import { useScrollReveal } from './hooks/useScrollReveal';
import { PricingProvider } from './hooks/usePricingPromo';
import { DocsRouterProvider, useDocsRouter } from './hooks/useDocsRouter';
import { DocsLayout } from './components/docs/DocsLayout';
import { trackPageView } from './tracking';

const AppContent: React.FC = () => {
  useScrollReveal();
  const { isDocsPortal } = useDocsRouter();

  useEffect(() => {
    trackPageView();
  }, []);

  if (isDocsPortal) {
    return <DocsLayout />;
  }

  return (
    <div className="min-h-screen flex flex-col bg-slate-50 text-slate-900">
      <Navbar />
      <main className="flex-1">
        <Hero />
        <ProblemSolution />
        <ScreenshotShowcase />
        <WhatYouGet />
        <PricingSection />
        <HowToBuy />
        <TrustSection />
        <FaqSection />
        <FinalCta />
      </main>
      <Footer />
      <StickyMobileCta />
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <PricingProvider>
      <DocsRouterProvider>
        <AppContent />
      </DocsRouterProvider>
    </PricingProvider>
  );
};

export default App;
