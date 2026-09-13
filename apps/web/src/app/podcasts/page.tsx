'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import {
  Sparkles,
  Search,
  Radio,
  Play,
  Pause,
  Clock,
  TrendingUp,
  Bookmark,
  Share2,
  ChevronLeft,
  ChevronRight,
  Volume2,
  SlidersHorizontal,
} from 'lucide-react';
import { PODCAST_SHELVES, PodcastShelf, PodcastShow } from '@/data/podcast-shelves';

export default function PodcastsPage() {
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [activeTrack, setActiveTrack] = useState<{
    show: PodcastShow;
    category: string;
    emoji: string;
  } | null>(null);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);

  // Filter shelves based on selected category & search query
  const filteredShelves = PODCAST_SHELVES.filter((shelf) => {
    if (selectedCategory !== 'all' && shelf.id !== parseInt(selectedCategory, 10)) {
      return false;
    }
    if (!searchQuery.trim()) return true;

    const query = searchQuery.toLowerCase();
    const matchesCategory = shelf.category.toLowerCase().includes(query);
    const matchesDesc = shelf.description.toLowerCase().includes(query);
    const matchesShows = shelf.shows.some(
      (s) =>
        s.title.toLowerCase().includes(query) ||
        s.creator.toLowerCase().includes(query) ||
        (s.tags && s.tags.some((t) => t.toLowerCase().includes(query)))
    );

    return matchesCategory || matchesDesc || matchesShows;
  });

  const handlePlayShow = (show: PodcastShow, categoryName: string, emoji: string) => {
    if (activeTrack?.show.id === show.id) {
      setIsPlaying(!isPlaying);
    } else {
      setActiveTrack({ show, category: categoryName, emoji });
      setIsPlaying(true);
    }
  };

  const scrollRail = (shelfId: number, direction: 'left' | 'right') => {
    const el = document.getElementById(`rail-${shelfId}`);
    if (el) {
      const scrollAmount = direction === 'left' ? -350 : 350;
      el.scrollBy({ left: scrollAmount, behavior: 'smooth' });
    }
  };

  return (
    <div className="min-h-screen bg-surface-inverse text-content-inverse font-sans pb-28">
      {/* HEADER / NAVIGATION */}
      <header className="sticky top-0 z-40 border-b border-border-strong bg-surface-inverse/90 backdrop-blur-xl">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3 sm:px-6">
          <div className="flex items-center gap-4">
            <Link
              href="/"
              className="flex items-center gap-1.5 text-xs font-bold text-content-muted hover:text-white transition-colors"
            >
              <ChevronLeft className="h-4 w-4" />
              <span>Back to Store</span>
            </Link>

            <div className="h-4 w-px bg-surface-inverse" />

            <div className="flex items-center gap-2.5">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-action-editorialBg text-white shadow-md shadow-subtle">
                <Radio className="h-5 w-5 animate-pulse" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h1 className="text-lg font-black tracking-tight text-white">
                    PODCAST RAIL STUDIO
                  </h1>
                  <span className="rounded-full bg-surface-editorialSubtle px-2 py-0.5 text-2xs font-bold text-content-editorial border border-border-editorial">
                    18 Shelves
                  </span>
                </div>
                <p className="text-2xs font-medium text-content-muted">
                  Curated Categories &amp; Streaming Rails
                </p>
              </div>
            </div>
          </div>

          {/* SEARCH BOX */}
          <div className="relative w-64 sm:w-80">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-content-muted" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search 18 podcast shelves & shows..."
              className="w-full rounded-xl border border-border-strong bg-surface-inverse/80 pl-9 pr-4 py-2 text-xs text-white placeholder:text-content-muted focus:border-border-editorial focus:outline-none focus:ring-1 focus:ring-border-editorial transition-all"
            />
          </div>
        </div>
      </header>

      {/* HERO SECTION */}
      <section className="relative overflow-hidden border-b border-border-strong bg-surface-inverse py-8">
        <div className="mx-auto max-w-7xl px-4 sm:px-6">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
            <div>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-surface-editorialSubtle px-3 py-1 text-xs font-bold text-content-editorial border border-border-editorial mb-3">
                <Sparkles className="h-3.5 w-3.5 text-content-editorial" />
                Custom Podcast Shelves
              </span>
              <h2 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
                Exclusive Audio Rails &amp; Show Shelves
              </h2>
              <p className="mt-1 max-w-2xl text-xs sm:text-sm text-content-muted">
                Only your designated 18 podcast categories: from Lifestyle &amp; Marketing to History, Comedy, Religion, and Popular Shows.
              </p>
            </div>

            {/* STATS QUICK CHIPS */}
            <div className="flex items-center gap-3">
              <div className="rounded-2xl border border-border-strong bg-surface-inverse/80 p-3 text-center min-w-[90px]">
                <div className="text-xl font-black text-content-editorial">18</div>
                <div className="text-2xs font-semibold text-content-muted uppercase">Shelves</div>
              </div>
              <div className="rounded-2xl border border-border-strong bg-surface-inverse/80 p-3 text-center min-w-[90px]">
                <div className="text-xl font-black text-content-accent">45+</div>
                <div className="text-2xs font-semibold text-content-muted uppercase">Shows</div>
              </div>
              <div className="rounded-2xl border border-border-strong bg-surface-inverse/80 p-3 text-center min-w-[90px]">
                <div className="text-xl font-black text-content-brand">100%</div>
                <div className="text-2xs font-semibold text-content-muted uppercase">Custom</div>
              </div>
            </div>
          </div>

          {/* CATEGORY NAV CHIPS */}
          <div className="mt-6 flex items-center gap-2 overflow-x-auto pb-2 scrollbar-none">
            <button
              onClick={() => setSelectedCategory('all')}
              className={`flex-shrink-0 rounded-xl px-3.5 py-1.5 text-xs font-bold transition-all ${
                selectedCategory === 'all'
                  ? 'bg-action-editorialBg text-white shadow-lg shadow-subtle'
                  : 'border border-border-strong bg-surface-inverse/80 text-content-muted hover:border-border-strong hover:text-white'
              }`}
            >
              🔥 All 18 Shelves
            </button>
            {PODCAST_SHELVES.map((shelf) => (
              <button
                key={shelf.id}
                onClick={() => setSelectedCategory(shelf.id.toString())}
                className={`flex-shrink-0 rounded-xl px-3 py-1.5 text-xs font-semibold transition-all flex items-center gap-1.5 ${
                  selectedCategory === shelf.id.toString()
                    ? 'bg-action-editorialBg text-white shadow-lg shadow-subtle font-bold'
                    : 'border border-border-strong bg-surface-inverse/80 text-content-muted hover:border-border-strong hover:text-white'
                }`}
              >
                <span>{shelf.emoji}</span>
                <span>{shelf.category}</span>
              </button>
            ))}
          </div>
        </div>
      </section>

      {/* RAILS CONTENT AREA */}
      <main className="mx-auto max-w-7xl px-4 sm:px-6 py-8 space-y-10">
        {filteredShelves.length === 0 ? (
          <div className="rounded-3xl border border-border-strong bg-surface-inverse/80 p-12 text-center">
            <SlidersHorizontal className="mx-auto h-12 w-12 text-content-secondary" />
            <h3 className="mt-4 text-base font-bold text-white">No podcast shows found</h3>
            <p className="mt-1 text-xs text-content-muted">
              Try adjusting your search query or reset to view all shelves.
            </p>
            <button
              onClick={() => {
                setSelectedCategory('all');
                setSearchQuery('');
              }}
              className="mt-4 rounded-xl bg-action-editorialBg px-4 py-2 text-xs font-bold text-white hover:bg-action-editorialHover"
            >
              Reset Filters
            </button>
          </div>
        ) : (
          filteredShelves.map((shelf) => (
            <section key={shelf.id} className="relative group">
              {/* SHELF HEADER */}
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2.5">
                  <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-surface-inverse border border-border-strong text-lg shadow-inner">
                    {shelf.emoji}
                  </div>
                  <div>
                    <h3 className="text-base sm:text-lg font-bold text-white tracking-wide flex items-center gap-2">
                      <span className="text-content-editorial font-black text-sm">#{shelf.id}</span>
                      {shelf.category}
                    </h3>
                    <p className="text-2xs text-content-muted line-clamp-1">{shelf.description}</p>
                  </div>
                </div>

                {/* SCROLL BUTTONS */}
                <div className="flex items-center gap-1.5">
                  <button
                    onClick={() => scrollRail(shelf.id, 'left')}
                    className="flex h-8 w-8 items-center justify-center rounded-lg border border-border-strong bg-surface-inverse text-content-muted hover:bg-surface-inverse hover:text-white transition-all active:scale-95"
                    aria-label="Scroll left"
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </button>
                  <button
                    onClick={() => scrollRail(shelf.id, 'right')}
                    className="flex h-8 w-8 items-center justify-center rounded-lg border border-border-strong bg-surface-inverse text-content-muted hover:bg-surface-inverse hover:text-white transition-all active:scale-95"
                    aria-label="Scroll right"
                  >
                    <ChevronRight className="h-4 w-4" />
                  </button>
                </div>
              </div>

              {/* HORIZONTAL SCROLLING RAIL */}
              <div
                id={`rail-${shelf.id}`}
                className="flex items-stretch gap-4 overflow-x-auto pb-4 pt-1 scrollbar-none snap-x snap-mandatory"
                style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
              >
                {shelf.shows.map((show) => {
                  const isCurrent = activeTrack?.show.id === show.id;
                  const isCurrentPlaying = isCurrent && isPlaying;

                  return (
                    <div
                      key={show.id}
                      className={`snap-start flex-shrink-0 w-[260px] sm:w-[290px] rounded-2xl border transition-all duration-300 p-4 flex flex-col justify-between group/card relative overflow-hidden ${
                        isCurrent
                          ? 'bg-surface-inverse border-border-editorial shadow-xl shadow-subtle'
                          : 'bg-surface-inverse/80 border-border-strong hover:border-border-strong hover:bg-surface-inverse hover:shadow-lg hover:shadow-subtle'
                      }`}
                    >
                      <div>
                        {/* TOP BAR IN CARD */}
                        <div className="flex items-center justify-between mb-3">
                          <span className="inline-flex items-center gap-1 rounded-md bg-surface-inverse border border-border-strong px-2 py-0.5 text-2xs font-semibold text-content-muted">
                            <Clock className="h-3 w-3 text-content-editorial" />
                            {show.duration || '30 min'}
                          </span>
                          <span className="flex items-center gap-1 text-2xs font-bold text-content-brand">
                            <TrendingUp className="h-3 w-3" />
                            {show.plays || '100K'} plays
                          </span>
                        </div>

                        {/* SHOW TITLE */}
                        <h4 className="font-bold text-sm text-white line-clamp-2 leading-snug group-hover/card:text-content-editorial transition-colors">
                          {show.title}
                        </h4>

                        {/* CREATOR */}
                        <p className="text-2xs font-medium text-content-muted mt-1 line-clamp-1">
                          {show.creator}
                        </p>

                        {/* TAGS */}
                        {show.tags && show.tags.length > 0 && (
                          <div className="mt-3 flex flex-wrap gap-1.5">
                            {show.tags.map((tag, idx) => (
                              <span
                                key={idx}
                                className="rounded-full bg-surface-inverse/90 border border-border-strong px-2 py-0.5 text-2xs font-medium text-content-muted"
                              >
                                #{tag}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>

                      {/* CARD BOTTOM ACTION BAR */}
                      <div className="mt-4 pt-3 border-t border-border-strong flex items-center justify-between">
                        <button
                          onClick={() => handlePlayShow(show, shelf.category, shelf.emoji)}
                          className={`flex items-center gap-2 rounded-xl px-3 py-1.5 text-xs font-bold transition-all ${
                            isCurrentPlaying
                              ? 'bg-action-primaryBg text-white shadow-md shadow-subtle'
                              : 'bg-action-editorialBg text-white hover:bg-action-editorialHover shadow-md shadow-subtle'
                          }`}
                        >
                          {isCurrentPlaying ? (
                            <>
                              <Pause className="h-3.5 w-3.5 fill-current" />
                              Playing
                            </>
                          ) : (
                            <>
                              <Play className="h-3.5 w-3.5 fill-current" />
                              Listen Rail
                            </>
                          )}
                        </button>

                        <div className="flex items-center gap-1 text-content-muted">
                          <button
                            onClick={() => alert(`Saved "${show.title}" to library!`)}
                            className="p-1.5 hover:text-white transition-colors"
                            title="Bookmark"
                          >
                            <Bookmark className="h-3.5 w-3.5" />
                          </button>
                          <button
                            onClick={() => alert(`Link copied for "${show.title}"`)}
                            className="p-1.5 hover:text-white transition-colors"
                            title="Share"
                          >
                            <Share2 className="h-3.5 w-3.5" />
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>
          ))
        )}
      </main>

      {/* PERSISTENT AUDIO PLAYER DOCK */}
      {activeTrack && (
        <div className="fixed bottom-0 left-0 right-0 z-50 border-t border-border-editorial bg-surface-inverse/90 backdrop-blur-2xl px-4 py-3 shadow-2xl">
          <div className="mx-auto flex max-w-7xl items-center justify-between gap-4">
            <div className="flex items-center gap-3 min-w-0">
              <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-xl bg-action-editorialBg text-white text-xl font-bold shadow-lg shadow-subtle">
                {activeTrack.emoji}
              </div>
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="rounded bg-surface-editorialSubtle px-1.5 py-0.5 text-2xs font-bold text-content-editorial border border-border-editorial">
                    {activeTrack.category}
                  </span>
                  <span className="text-2xs text-content-muted">Playing from Rail</span>
                </div>
                <h4 className="text-xs sm:text-sm font-bold text-white truncate max-w-xs sm:max-w-md">
                  {activeTrack.show.title}
                </h4>
                <p className="text-2xs text-content-muted truncate">
                  {activeTrack.show.creator}
                </p>
              </div>
            </div>

            {/* PLAYER CONTROLS */}
            <div className="flex items-center gap-4">
              <button
                onClick={() => setIsPlaying(!isPlaying)}
                className="flex h-10 w-10 items-center justify-center rounded-full bg-action-editorialBg text-white hover:bg-action-editorialHover shadow-lg shadow-subtle transition-all active:scale-95"
              >
                {isPlaying ? (
                  <Pause className="h-5 w-5 fill-current" />
                ) : (
                  <Play className="h-5 w-5 fill-current ml-0.5" />
                )}
              </button>
              <div className="hidden sm:flex items-center gap-2 text-content-muted">
                <Volume2 className="h-4 w-4" />
                <div className="h-1.5 w-24 rounded-full bg-surface-inverse overflow-hidden">
                  <div className="h-full w-3/4 rounded-full bg-action-editorialBg" />
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
