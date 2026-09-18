import React from 'react';
import { Navbar } from './components/Navbar';
import { Hero } from './components/Hero';
import { FeatureGrid } from './components/FeatureGrid';
import { AdaptiveBusinessSwitcher } from './components/AdaptiveBusinessSwitcher';
import { ScreenshotShowcase } from './components/ScreenshotShowcase';
import { PricingSection } from './components/PricingSection';
import { FaqSection } from './components/FaqSection';
import { StickyMobileCta } from './components/StickyMobileCta';
import { Footer } from './components/Footer';
import { useScrollReveal } from './hooks/useScrollReveal';
import { PricingProvider } from './hooks/usePricingPromo';
import { DocsRouterProvider, useDocsRouter } from './hooks/useDocsRouter';
import { DocsLayout } from './components/docs/DocsLayout';

const AppContent: React.FC = () => {
  useScrollReveal();
  const { isDocsPortal } = useDocsRouter();

  if (isDocsPortal) {
    return <DocsLayout />;
  }

  return (
    <div className="min-h-screen flex flex-col bg-slate-50 text-slate-900">
      <Navbar />
      <main className="flex-1">
        <Hero />
        <FeatureGrid />
        <AdaptiveBusinessSwitcher />
        <ScreenshotShowcase />
        <PricingSection />
        <FaqSection />
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
