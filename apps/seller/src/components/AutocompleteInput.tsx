'use client';

import React, { useState, useRef, useEffect, useMemo } from 'react';
import { Search, Check, ChevronDown, X } from 'lucide-react';

interface AutocompleteInputProps {
  value: string;
  onChange: (val: string) => void;
  options: string[];
  placeholder?: string;
  label?: string;
  required?: boolean;
  helperText?: string;
  className?: string;
  inputClassName?: string;
  disabled?: boolean;
  id?: string;
}

export function AutocompleteInput({
  value,
  onChange,
  options,
  placeholder = 'Type to search or enter custom...',
  label,
  required = false,
  helperText,
  className = '',
  inputClassName = '',
  disabled = false,
  id
}: AutocompleteInputProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Filter options based on user text with smart multi-token and prefix matching
  const filteredOptions = useMemo(() => {
    const trimmed = (value || '').trim().toLowerCase();
    if (!trimmed) {
      return options.slice(0, 50);
    }

    const queryTokens = trimmed.split(/[\s+,]+/).filter(Boolean);
    if (queryTokens.length === 0) {
      return options.slice(0, 50);
    }

    const prefixMatches: string[] = [];
    const tokenMatches: string[] = [];

    for (let i = 0; i < options.length; i++) {
      const opt = options[i];
      const lower = opt.toLowerCase();

      // Priority 1: Full phrase prefix match
      if (lower.startsWith(trimmed)) {
        prefixMatches.push(opt);
        if (prefixMatches.length >= 60) break;
        continue;
      }

      // Priority 2: Multi-token match (all keywords present in any order, e.g. "amox clav", "para 650")
      const allTokensPresent = queryTokens.every(tok => lower.includes(tok));
      if (allTokensPresent) {
        tokenMatches.push(opt);
        if (prefixMatches.length + tokenMatches.length >= 80) break;
      }
    }

    return [...prefixMatches, ...tokenMatches].slice(0, 60);
  }, [value, options]);

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

  // Reset highlight index when filtered list changes
  useEffect(() => {
    setHighlightedIndex(0);
  }, [filteredOptions]);

  const selectOption = (opt: string) => {
    onChange(opt);
    setIsOpen(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (!isOpen) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        setIsOpen(true);
        e.preventDefault();
      }
      return;
    }

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlightedIndex(prev => (prev + 1) % Math.max(1, filteredOptions.length));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlightedIndex(prev => (prev - 1 + filteredOptions.length) % Math.max(1, filteredOptions.length));
    } else if (e.key === 'Tab' || e.key === 'Enter') {
      // Auto-fill highlighted suggestion on Tab or Enter
      if (filteredOptions.length > 0 && highlightedIndex >= 0 && highlightedIndex < filteredOptions.length) {
        e.preventDefault();
        selectOption(filteredOptions[highlightedIndex]);
      } else {
        setIsOpen(false);
      }
    } else if (e.key === 'Escape') {
      setIsOpen(false);
    }
  };

  return (
    <div className={`relative ${className}`} ref={containerRef}>
      {label && (
        <label
          htmlFor={id}
          className="block text-2xs font-bold uppercase text-content-muted mb-1 flex items-center justify-between"
        >
          <span>{label} {required && <span className="text-content-danger">*</span>}</span>
          {isOpen && filteredOptions.length > 0 && (
            <span className="text-3xs font-medium text-content-muted lowercase">
              Press <kbd className="px-1 py-0.5 bg-surface-subtle border rounded text-[10px] font-mono">Tab</kbd> to autofill
            </span>
          )}
        </label>
      )}

      <div className="relative flex items-center">
        <Search className="w-3.5 h-3.5 absolute left-3 text-content-muted pointer-events-none" />
        <input
          id={id}
          ref={inputRef}
          type="text"
          value={value}
          disabled={disabled}
          placeholder={placeholder}
          onFocus={() => setIsOpen(true)}
          onChange={e => {
            onChange(e.target.value);
            setIsOpen(true);
          }}
          onKeyDown={handleKeyDown}
          autoComplete="off"
          className={`w-full bg-surface-subtle border border-border-default rounded-xl pl-8 pr-14 py-2 text-xs text-content-primary focus:outline-none focus:border-border-accent ${inputClassName}`}
        />

        <div className="absolute right-2 flex items-center space-x-1">
          {value && !disabled && (
            <button
              type="button"
              onClick={() => {
                onChange('');
                inputRef.current?.focus();
              }}
              className="p-1 text-content-muted hover:text-content-primary rounded transition"
              title="Clear"
            >
              <X className="w-3 h-3" />
            </button>
          )}
          <button
            type="button"
            tabIndex={-1}
            onClick={() => setIsOpen(!isOpen)}
            className="p-1 text-content-muted hover:text-content-primary rounded transition"
          >
            <ChevronDown className={`w-3.5 h-3.5 transition-transform duration-150 ${isOpen ? 'rotate-180' : ''}`} />
          </button>
        </div>
      </div>

      {helperText && (
        <p className="text-3xs text-content-muted mt-1">{helperText}</p>
      )}

      {/* Floating Suggestions Dropdown */}
      {isOpen && (
        <div className="absolute z-50 left-0 right-0 mt-1 max-h-56 overflow-y-auto bg-white border border-border-default rounded-xl shadow-xl py-1 text-xs divide-y divide-border-subtle animate-in fade-in zoom-in-95 duration-100">
          {filteredOptions.length === 0 ? (
            <div className="px-3 py-2 text-content-muted text-2xs italic">
              No standard matches found. Custom entry will be saved.
            </div>
          ) : (
            filteredOptions.map((opt, idx) => {
              const isSelected = opt.toLowerCase() === (value || '').toLowerCase();
              const isHighlighted = idx === highlightedIndex;

              return (
                <div
                  key={`${opt}-${idx}`}
                  onMouseEnter={() => setHighlightedIndex(idx)}
                  onClick={() => selectOption(opt)}
                  className={`px-3 py-2 cursor-pointer flex items-center justify-between text-left transition-colors ${
                    isHighlighted ? 'bg-surface-subtle text-content-primary font-medium' : 'text-content-primary'
                  }`}
                >
                  <span className="truncate pr-2">{opt}</span>
                  <div className="flex items-center space-x-1 shrink-0">
                    {idx === highlightedIndex && (
                      <span className="text-[10px] font-mono text-content-muted bg-slate-100 px-1 py-0.5 rounded">
                        Tab ⇥
                      </span>
                    )}
                    {isSelected && <Check className="w-3.5 h-3.5 text-content-brand shrink-0" />}
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}
