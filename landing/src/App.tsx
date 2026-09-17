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

export const App: React.FC = () => {
  useScrollReveal();

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

export default App;
