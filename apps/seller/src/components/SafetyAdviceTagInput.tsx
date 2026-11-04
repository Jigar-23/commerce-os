'use client';

import React, { useState, useRef, useEffect, useMemo } from 'react';
import { Hash, X, Plus, Search, ShieldAlert, AlertTriangle, CheckCircle, Info } from 'lucide-react';
import { COMMON_SAFETY_ADVICE, SafetyAdviceItem } from '../lib/pharmaReferenceData';

interface SafetyAdviceTagInputProps {
  tags: string[];
  onChange: (tags: string[]) => void;
  label?: string;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
}

export function SafetyAdviceTagInput({
  tags = [],
  onChange,
  label = 'Safety Advice & Clinical Warnings',
  placeholder = 'Type # or search warnings (e.g. alcohol, pregnancy, kidney, food)...',
  disabled = false,
  className = ''
}: SafetyAdviceTagInputProps) {
  const [inputValue, setInputValue] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Available categories
  const categories = useMemo(() => {
    const cats = Array.from(new Set(COMMON_SAFETY_ADVICE.map(i => i.category)));
    return ['ALL', ...cats];
  }, []);

  // Filter advice options based on input value and selected category
  const filteredAdvice = useMemo(() => {
    let list = COMMON_SAFETY_ADVICE;

    if (selectedCategory !== 'ALL') {
      list = list.filter(item => item.category === selectedCategory);
    }

    const query = inputValue.trim().replace(/^#+/, '').toLowerCase();
    if (!query) {
      return list;
    }

    return list.filter(item => 
      item.tag.toLowerCase().includes(query) ||
      item.label.toLowerCase().includes(query) ||
      item.category.toLowerCase().includes(query)
    );
  }, [inputValue, selectedCategory]);

  // Close dropdown on outside click
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const addTag = (rawTag: string) => {
    let cleanTag = rawTag.trim();
    if (!cleanTag.startsWith('#')) {
      cleanTag = `#${cleanTag}`;
    }
    if (!tags.includes(cleanTag)) {
      onChange([...tags, cleanTag]);
    }
    setInputValue('');
    inputRef.current?.focus();
  };

  const removeTag = (indexToRemove: number) => {
    onChange(tags.filter((_, idx) => idx !== indexToRemove));
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      if (inputValue.trim()) {
        addTag(inputValue);
      }
    } else if (e.key === 'Backspace' && !inputValue && tags.length > 0) {
      // Remove last tag on backspace if input is empty
      removeTag(tags.length - 1);
    } else if (e.key === 'Escape') {
      setIsOpen(false);
    }
  };

  const getSeverityBadge = (severity: SafetyAdviceItem['severity']) => {
    switch (severity) {
      case 'CRITICAL':
        return (
          <span className="inline-flex items-center space-x-1 px-1.5 py-0.5 rounded text-[10px] font-bold bg-red-100 text-red-800 border border-red-200">
            <ShieldAlert className="w-2.5 h-2.5" />
            <span>CRITICAL</span>
          </span>
        );
      case 'CAUTION':
        return (
          <span className="inline-flex items-center space-x-1 px-1.5 py-0.5 rounded text-[10px] font-bold bg-amber-100 text-amber-800 border border-amber-200">
            <AlertTriangle className="w-2.5 h-2.5" />
            <span>CAUTION</span>
          </span>
        );
      case 'SAFE':
        return (
          <span className="inline-flex items-center space-x-1 px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-200">
            <CheckCircle className="w-2.5 h-2.5" />
            <span>SAFE</span>
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center space-x-1 px-1.5 py-0.5 rounded text-[10px] font-bold bg-blue-100 text-blue-800 border border-blue-200">
            <Info className="w-2.5 h-2.5" />
            <span>INFO</span>
          </span>
        );
    }
  };

  return (
    <div className={`space-y-2 ${className}`} ref={containerRef}>
      {label && (
        <div className="flex items-center justify-between">
          <label className="block text-2xs font-bold uppercase text-content-muted flex items-center space-x-1.5">
            <Hash className="w-3.5 h-3.5 text-content-brand" />
            <span>{label}</span>
          </label>
          <span className="text-3xs text-content-muted">
            {tags.length} warning {tags.length === 1 ? 'tag' : 'tags'} added
          </span>
        </div>
      )}

      {/* Tags Display Container & Input Box */}
      <div 
        onClick={() => inputRef.current?.focus()}
        className={`min-h-[46px] p-2 bg-surface-subtle border border-border-default rounded-xl flex flex-wrap items-center gap-1.5 transition-colors focus-within:border-border-accent ${
          disabled ? 'opacity-60 cursor-not-allowed' : 'cursor-text'
        }`}
      >
        {/* Render Selected Hashtag Badges */}
        {tags.map((tag, idx) => {
          const isCritical = tag.toLowerCase().includes('critical') || tag.toLowerCase().includes('unsafe') || tag.toLowerCase().includes('contraindicated');
          const isCaution = tag.toLowerCase().includes('caution') || tag.toLowerCase().includes('avoid');

          return (
            <span
              key={`${tag}-${idx}`}
              className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold shadow-xs animate-in fade-in duration-150 transition-all ${
                isCritical
                  ? 'bg-red-50 text-red-900 border border-red-200 hover:bg-red-100'
                  : isCaution
                  ? 'bg-amber-50 text-amber-900 border border-amber-200 hover:bg-amber-100'
                  : 'bg-indigo-50 text-indigo-900 border border-indigo-200 hover:bg-indigo-100'
              }`}
            >
              <Hash className="w-3 h-3 opacity-60 shrink-0" />
              <span className="break-all">{tag.startsWith('#') ? tag.slice(1) : tag}</span>
              {!disabled && (
                <button
                  type="button"
                  tabIndex={-1}
                  onClick={(e) => {
                    e.stopPropagation();
                    removeTag(idx);
                  }}
                  className="w-4 h-4 rounded-full flex items-center justify-center hover:bg-black/10 transition-colors text-slate-500 hover:text-slate-900 ml-0.5 shrink-0"
                  title="Remove warning"
                >
                  <X className="w-3 h-3" />
                </button>
              )}
            </span>
          );
        })}

        {/* Input field */}
        <div className="flex-1 min-w-[200px] flex items-center">
          <input
            ref={inputRef}
            type="text"
            disabled={disabled}
            value={inputValue}
            placeholder={tags.length === 0 ? placeholder : 'Add another #tag...'}
            onFocus={() => setIsOpen(true)}
            onChange={e => {
              setInputValue(e.target.value);
              setIsOpen(true);
            }}
            onKeyDown={handleKeyDown}
            className="w-full bg-transparent border-0 outline-none p-1 text-xs text-content-primary placeholder:text-content-muted"
          />
        </div>
      </div>

      {/* Floating Suggestions Panel (YouTube Hashtag style) */}
      {isOpen && !disabled && (
        <div className="relative">
          <div className="absolute z-50 left-0 right-0 mt-1 bg-white border border-border-default rounded-2xl shadow-2xl p-3 space-y-3 animate-in fade-in zoom-in-95 duration-100">
            {/* Category filter pills */}
            <div className="flex items-center gap-1 overflow-x-auto pb-1 scrollbar-none text-2xs">
              <span className="text-3xs font-bold text-slate-400 uppercase mr-1 shrink-0">Filter:</span>
              {categories.map(cat => (
                <button
                  key={cat}
                  type="button"
                  onClick={() => setSelectedCategory(cat)}
                  className={`px-2 py-0.5 rounded-full font-bold whitespace-nowrap transition-colors ${
                    selectedCategory === cat
                      ? 'bg-slate-900 text-white'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>

            {/* Quick Helper / Custom Tag Prompt */}
            {inputValue.trim() && (
              <div 
                onClick={() => addTag(inputValue)}
                className="p-2 bg-indigo-50 border border-indigo-100 rounded-xl cursor-pointer hover:bg-indigo-100 flex items-center justify-between text-xs font-semibold text-indigo-900"
              >
                <div className="flex items-center space-x-2">
                  <Plus className="w-3.5 h-3.5 text-indigo-700" />
                  <span>Add custom tag: <strong className="font-mono">#{inputValue.replace(/^#+/, '')}</strong></span>
                </div>
                <span className="text-[10px] bg-white px-2 py-0.5 rounded border border-indigo-200 font-mono">
                  Press Enter ↵
                </span>
              </div>
            )}

            {/* Suggestions List */}
            <div className="max-h-56 overflow-y-auto space-y-1 pr-1 divide-y divide-slate-100">
              {filteredAdvice.length === 0 ? (
                <div className="py-4 text-center text-slate-400 text-xs">
                  No matching warning found. Press Enter to create a custom hashtag tag.
                </div>
              ) : (
                filteredAdvice.map(item => {
                  const isAdded = tags.some(t => t.toLowerCase() === item.tag.toLowerCase());

                  return (
                    <div
                      key={item.id}
                      onClick={() => {
                        if (!isAdded) {
                          addTag(item.tag);
                        }
                      }}
                      className={`pt-2 pb-2 px-2 rounded-xl flex items-start justify-between gap-3 text-xs cursor-pointer transition-colors ${
                        isAdded
                          ? 'opacity-40 bg-slate-50 cursor-default'
                          : 'hover:bg-slate-50 active:bg-slate-100'
                      }`}
                    >
                      <div className="space-y-1 min-w-0 flex-1">
                        <div className="flex items-center space-x-2">
                          <span className="font-bold text-slate-900 font-mono text-[11px] truncate">
                            {item.tag}
                          </span>
                          {getSeverityBadge(item.severity)}
                        </div>
                        <p className="text-2xs text-slate-500 leading-tight">
                          {item.label}
                        </p>
                      </div>

                      <div className="shrink-0 pt-0.5">
                        {isAdded ? (
                          <span className="text-[10px] font-bold text-emerald-600 bg-emerald-50 px-2 py-0.5 rounded">
                            Added ✓
                          </span>
                        ) : (
                          <button
                            type="button"
                            className="px-2 py-1 rounded-lg bg-slate-100 hover:bg-slate-900 hover:text-white text-slate-700 text-2xs font-bold transition-colors"
                          >
                            + Add
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })
              )}
            </div>

            {/* Footer tip */}
            <div className="border-t border-slate-100 pt-2 flex items-center justify-between text-3xs text-slate-400">
              <span>Click any warning to add as a YouTube-style hashtag</span>
              <button
                type="button"
                onClick={() => setIsOpen(false)}
                className="font-bold text-slate-600 hover:underline"
              >
                Close ✕
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
