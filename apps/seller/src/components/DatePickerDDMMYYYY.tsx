'use client';

import React, { useState, useRef, useEffect } from 'react';
import { Calendar as CalendarIcon, ChevronLeft, ChevronRight, X } from 'lucide-react';

interface DatePickerDDMMYYYYProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  required?: boolean;
  disabled?: boolean;
}

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];

const SHORT_DAYS = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

export function parseDDMMYYYY(str: string): { day: number; month: number; year: number } | null {
  if (!str) return null;
  const trimmed = str.trim();
  
  // DD/MM/YYYY or DD-MM-YYYY
  const partsSlash = trimmed.split(/[/.-]/);
  if (partsSlash.length === 3) {
    // If first part is 4 digits -> YYYY-MM-DD format
    if (partsSlash[0].length === 4) {
      const y = parseInt(partsSlash[0], 10);
      const m = parseInt(partsSlash[1], 10);
      const d = parseInt(partsSlash[2], 10);
      if (!isNaN(d) && !isNaN(m) && !isNaN(y) && m >= 1 && m <= 12 && d >= 1 && d <= 31) {
        return { day: d, month: m - 1, year: y };
      }
    } else {
      // DD/MM/YYYY format
      const d = parseInt(partsSlash[0], 10);
      const m = parseInt(partsSlash[1], 10);
      const y = parseInt(partsSlash[2], 10);
      if (!isNaN(d) && !isNaN(m) && !isNaN(y) && m >= 1 && m <= 12 && d >= 1 && d <= 31 && y >= 1900) {
        return { day: d, month: m - 1, year: y };
      }
    }
  }
  return null;
}

export function formatDDMMYYYY(day: number, month: number, year: number): string {
  const dd = String(day).padStart(2, '0');
  const mm = String(month + 1).padStart(2, '0');
  return `${dd}/${mm}/${year}`;
}

export default function DatePickerDDMMYYYY({
  value,
  onChange,
  placeholder = 'DD/MM/YYYY',
  className = '',
  required = false,
  disabled = false,
}: DatePickerDDMMYYYYProps) {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Derive initial calendar view from current value or today
  const parsed = parseDDMMYYYY(value);
  const today = new Date();
  const [viewYear, setViewYear] = useState<number>(parsed?.year ?? today.getFullYear());
  const [viewMonth, setViewMonth] = useState<number>(parsed?.month ?? today.getMonth());

  // Update view when external value changes
  useEffect(() => {
    if (parsed) {
      setViewYear(parsed.year);
      setViewMonth(parsed.month);
    }
  }, [value]);

  // Close calendar popover on outside click
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  // Handle manual typing with smart DD/MM/YYYY masking
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value;
    // Allow delete
    if (raw.length < value.length) {
      onChange(raw);
      return;
    }
    // Extract only digits up to 8 chars
    const digits = raw.replace(/\D/g, '').slice(0, 8);
    if (digits.length === 0) {
      onChange('');
      return;
    }
    let formatted = digits.slice(0, 2);
    if (digits.length >= 3) {
      formatted += '/' + digits.slice(2, 4);
    }
    if (digits.length >= 5) {
      formatted += '/' + digits.slice(4, 8);
    }
    onChange(formatted);
  };

  const handlePrevMonth = () => {
    if (viewMonth === 0) {
      setViewMonth(11);
      setViewYear(prev => prev - 1);
    } else {
      setViewMonth(prev => prev - 1);
    }
  };

  const handleNextMonth = () => {
    if (viewMonth === 11) {
      setViewMonth(0);
      setViewYear(prev => prev + 1);
    } else {
      setViewMonth(prev => prev + 1);
    }
  };

  const handleSelectDay = (day: number) => {
    const formatted = formatDDMMYYYY(day, viewMonth, viewYear);
    onChange(formatted);
    setIsOpen(false);
  };

  const handleSelectToday = () => {
    const tDay = today.getDate();
    const tMonth = today.getMonth();
    const tYear = today.getFullYear();
    setViewMonth(tMonth);
    setViewYear(tYear);
    onChange(formatDDMMYYYY(tDay, tMonth, tYear));
    setIsOpen(false);
  };

  const handleClear = () => {
    onChange('');
    setIsOpen(false);
  };

  // Calendar matrix calculations
  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
  const firstDayOfWeek = new Date(viewYear, viewMonth, 1).getDay(); // 0 = Sunday

  // Generate range of years for quick dropdown (past 15 years to future 15 years)
  const currentYear = today.getFullYear();
  const years = Array.from({ length: 31 }, (_, i) => currentYear - 15 + i);

  return (
    <div className={`relative ${className}`} ref={containerRef}>
      <div className="relative flex items-center">
        <input
          type="text"
          value={value}
          onChange={handleInputChange}
          placeholder={placeholder}
          required={required}
          disabled={disabled}
          maxLength={10}
          className="w-full bg-surface-subtle border border-border-default rounded-xl px-3 py-2 pr-9 text-xs text-content-primary focus:outline-none focus:border-border-accent font-mono placeholder:font-sans placeholder:text-content-muted"
        />
        <button
          type="button"
          disabled={disabled}
          onClick={() => setIsOpen(prev => !prev)}
          className="absolute right-2.5 text-content-muted hover:text-content-primary p-0.5 rounded transition"
          title="Open calendar (DD/MM/YYYY)"
        >
          <CalendarIcon className="w-4 h-4" />
        </button>
      </div>

      {isOpen && (
        <div className="absolute left-0 top-full mt-1.5 z-50 bg-white border border-border-default rounded-2xl shadow-xl p-3.5 w-64 text-content-primary animate-in fade-in zoom-in-95 duration-100">
          {/* Header Controls: Month, Year, Arrows */}
          <div className="flex items-center justify-between gap-1 mb-3">
            <button
              type="button"
              onClick={handlePrevMonth}
              className="p-1 rounded-lg text-content-muted hover:text-content-primary hover:bg-surface-subtle transition"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>

            <div className="flex items-center space-x-1 text-xs">
              <select
                value={viewMonth}
                onChange={e => setViewMonth(parseInt(e.target.value, 10))}
                className="bg-surface-subtle border border-border-default rounded-lg px-2 py-1 text-xs font-semibold text-content-primary focus:outline-none cursor-pointer"
              >
                {MONTH_NAMES.map((name, idx) => (
                  <option key={idx} value={idx}>
                    {name.slice(0, 3)}
                  </option>
                ))}
              </select>

              <select
                value={viewYear}
                onChange={e => setViewYear(parseInt(e.target.value, 10))}
                className="bg-surface-subtle border border-border-default rounded-lg px-2 py-1 text-xs font-semibold text-content-primary focus:outline-none cursor-pointer font-mono"
              >
                {years.map(y => (
                  <option key={y} value={y}>
                    {y}
                  </option>
                ))}
              </select>
            </div>

            <button
              type="button"
              onClick={handleNextMonth}
              className="p-1 rounded-lg text-content-muted hover:text-content-primary hover:bg-surface-subtle transition"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>

          {/* Weekday Headers */}
          <div className="grid grid-cols-7 gap-1 text-center mb-1">
            {SHORT_DAYS.map(day => (
              <span key={day} className="text-3xs font-bold text-content-muted uppercase">
                {day}
              </span>
            ))}
          </div>

          {/* Days Grid */}
          <div className="grid grid-cols-7 gap-1 text-center">
            {Array.from({ length: firstDayOfWeek }).map((_, i) => (
              <div key={`empty-${i}`} className="w-7 h-7" />
            ))}

            {Array.from({ length: daysInMonth }).map((_, i) => {
              const dayNum = i + 1;
              const isSelected = parsed?.day === dayNum && parsed?.month === viewMonth && parsed?.year === viewYear;
              const isToday = today.getDate() === dayNum && today.getMonth() === viewMonth && today.getFullYear() === viewYear;

              return (
                <button
                  key={`day-${dayNum}`}
                  type="button"
                  onClick={() => handleSelectDay(dayNum)}
                  className={`w-7 h-7 rounded-lg text-xs flex items-center justify-center transition font-mono ${
                    isSelected
                      ? 'bg-action-speedBg text-white font-bold shadow-sm'
                      : isToday
                      ? 'border border-border-accent font-bold text-content-brand hover:bg-surface-brandSubtle'
                      : 'hover:bg-surface-subtle text-content-primary'
                  }`}
                >
                  {dayNum}
                </button>
              );
            })}
          </div>

          {/* Footer Quick Actions */}
          <div className="mt-3 pt-2.5 border-t border-border-default flex items-center justify-between text-2xs">
            <button
              type="button"
              onClick={handleSelectToday}
              className="font-semibold text-content-brand hover:underline"
            >
              Today
            </button>
            <button
              type="button"
              onClick={handleClear}
              className="font-semibold text-content-muted hover:text-content-danger"
            >
              Clear
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
