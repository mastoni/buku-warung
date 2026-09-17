import React, { useState } from 'react';
import { SCREENSHOTS, ScreenshotItem } from '../data/landingData';

export const ScreenshotShowcase: React.FC = () => {
  const [selectedId, setSelectedId] = useState<string>(SCREENSHOTS[0].id);
  const activeScreenshot = SCREENSHOTS.find((s) => s.id === selectedId) || SCREENSHOTS[0];

  return (
    <section id="tampilan" className="py-16 md:py-24 bg-slate-50">
      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        {/* Section Header */}
        <div className="text-center max-w-2xl mx-auto space-y-3 mb-12 sm:mb-16">
          <div className="inline-block px-3 py-1 rounded-lg bg-emerald-100 text-emerald-800 text-xs font-bold uppercase tracking-wider">
            Tampilan Asli Aplikasi
          </div>
          <h2 className="text-2xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Lihat Langsung Kemudahan Antarmuka Buku Warung
          </h2>
          <p className="text-sm sm:text-base text-slate-600">
            Tampilan bersih, teks jelas, tombol besar, dan responsif untuk kenyamanan operasional kasir seharian.
          </p>
        </div>

        {/* Screenshot Selector & Display */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
          {/* Navigation Pill List */}
          <div className="lg:col-span-5 space-y-2 max-h-[500px] overflow-y-auto pr-2">
            {SCREENSHOTS.map((item: ScreenshotItem) => {
              const isActive = item.id === selectedId;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setSelectedId(item.id)}
                  className={`w-full text-left p-3.5 sm:p-4 rounded-xl transition-all border flex items-start gap-3 cursor-pointer ${
                    isActive
                      ? 'bg-white border-emerald-500 shadow-md ring-2 ring-emerald-500/10'
                      : 'bg-white/60 border-slate-200/80 hover:bg-white hover:border-slate-300'
                  }`}
                >
                  <div
                    className={`w-2 h-2 rounded-full mt-2 shrink-0 ${
                      isActive ? 'bg-emerald-600 ring-4 ring-emerald-100' : 'bg-slate-300'
                    }`}
                  />
                  <div>
                    <div className="flex items-center gap-2">
                      <h3
                        className={`font-bold text-sm sm:text-base ${
                          isActive ? 'text-emerald-900' : 'text-slate-800'
                        }`}
                      >
                        {item.title}
                      </h3>
                      <span className="text-[10px] font-semibold uppercase px-2 py-0.5 rounded bg-slate-100 text-slate-600">
                        {item.category}
                      </span>
                    </div>
                    <p className="text-xs text-slate-500 mt-1 line-clamp-2">{item.description}</p>
                  </div>
                </button>
              );
            })}
          </div>

          {/* Screenshot Display Frame */}
          <div className="lg:col-span-7 flex justify-center">
            <div className="relative max-w-xs sm:max-w-sm w-full bg-slate-900 rounded-[2.5rem] p-3 shadow-2xl border-4 border-slate-800">
              {/* Phone Camera Notch */}
              <div className="w-24 h-4 bg-slate-800 rounded-full mx-auto mb-2" />
              {/* Phone Screen */}
              <div className="rounded-[2rem] overflow-hidden bg-slate-100 aspect-9/18 flex items-center justify-center border border-slate-700">
                <img
                  src={activeScreenshot.imageSrc}
                  alt={activeScreenshot.title}
                  className="w-full h-full object-contain bg-slate-50"
                  loading="lazy"
                />
              </div>
              {/* Caption */}
              <div className="text-center pt-3 pb-1 text-xs text-slate-400 font-medium">
                {activeScreenshot.title} — {activeScreenshot.category}
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};
