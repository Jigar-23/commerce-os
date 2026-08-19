import React from 'react';
import {
  Flame,
  Pill,
  Activity,
  Sparkles,
  ShoppingBag,
  Apple,
  Coffee,
  HeartPulse,
  Baby,
  ShieldCheck,
} from 'lucide-react';
import { cn } from '@commerce-os/design-system';

export interface CategoryItem {
  id: string;
  label: string;
  sublabel?: string;
  icon: React.ReactNode;
}

export const DEFAULT_COMMERCE_CATEGORIES: CategoryItem[] = [
  { id: 'ANALGESIC', label: 'Pain & Fever', sublabel: 'Fast Relief', icon: <Flame className="h-5 w-5" /> },
  { id: 'ANTIBIOTIC', label: 'Antibiotics', sublabel: 'Infection Care', icon: <Pill className="h-5 w-5" /> },
  { id: 'ANTACID', label: 'Digestive Care', sublabel: 'Antacids & Gut', icon: <ShieldCheck className="h-5 w-5" /> },
  { id: 'DIABETIC', label: 'Diabetes Care', sublabel: 'Insulin & Gluco', icon: <Activity className="h-5 w-5" /> },
  { id: 'WELLNESS', label: 'Daily Wellness', sublabel: 'Vitamins & Zinc', icon: <Sparkles className="h-5 w-5" /> },
  { id: 'CARDIAC', label: 'Cardiac & BP', sublabel: 'Heart Health', icon: <HeartPulse className="h-5 w-5" /> },
  { id: 'BABY', label: 'Mother & Baby', sublabel: 'Infant Nutrition', icon: <Baby className="h-5 w-5" /> },
  { id: 'ESSENTIALS', label: 'Daily Essentials', sublabel: 'Home Care', icon: <ShoppingBag className="h-5 w-5" /> },
];

export interface CommerceCategoryShowcaseProps {
  categories?: CategoryItem[];
  selectedCategory: string | null;
  onSelectCategory: (categoryId: string | null) => void;
  className?: string;
}

export const CommerceCategoryShowcase: React.FC<CommerceCategoryShowcaseProps> = ({
  categories = DEFAULT_COMMERCE_CATEGORIES,
  selectedCategory,
  onSelectCategory,
  className = '',
}) => {
  return (
    <div
      className={cn(
        'flex items-center gap-3 overflow-x-auto pb-2 scrollbar-none select-none scroll-smooth',
        className
      )}
    >
      {/* ALL CATEGORIES CHIP */}
      <button
        type="button"
        onClick={() => onSelectCategory(null)}
        className={cn(
          'shrink-0 flex flex-col items-center justify-center p-3 rounded-xl border transition-all active:scale-95 text-center min-w-[92px] cursor-pointer',
          !selectedCategory
            ? 'bg-surface-inverse text-content-inverse border-surface-inverse shadow-subtle'
            : 'bg-surface-card text-content-primary border-border-default hover:border-border-strong hover:bg-surface-subtle shadow-subtle'
        )}
      >
        <div
          className={cn(
            'w-11 h-11 rounded-lg flex items-center justify-center mb-1.5',
            !selectedCategory ? 'bg-surface-inverse text-content-inverse' : 'bg-surface-subtle text-content-secondary'
          )}
        >
          <Sparkles className="h-5 w-5" />
        </div>
        <span className="text-xs font-bold leading-tight">All Products</span>
        <span className={cn('text-2xs mt-0.5', !selectedCategory ? 'text-content-muted' : 'text-content-muted')}>
          Full Selection
        </span>
      </button>

      {/* DYNAMIC CATEGORY CHIPS */}
      {categories.map((cat) => {
        const isSelected = selectedCategory === cat.id;
        return (
          <button
            key={cat.id}
            type="button"
            onClick={() => onSelectCategory(isSelected ? null : cat.id)}
            className={cn(
              'shrink-0 flex flex-col items-center justify-center p-3 rounded-xl border transition-all active:scale-95 text-center min-w-[92px] cursor-pointer',
              isSelected
                ? 'bg-action-primaryBg text-action-primaryText border-border-brand shadow-subtle ring-2 ring-border-brand/20'
                : 'bg-surface-card text-content-primary border-border-default hover:border-border-strong hover:bg-surface-subtle shadow-subtle'
            )}
          >
            <div
              className={cn(
                'w-11 h-11 rounded-lg flex items-center justify-center mb-1.5',
                isSelected ? 'bg-action-primaryHover text-action-primaryText' : 'bg-surface-brandSubtle text-content-brand'
              )}
            >
              {cat.icon}
            </div>
            <span className="text-xs font-bold leading-tight">{cat.label}</span>
            {cat.sublabel && (
              <span
                className={cn(
                  'text-2xs mt-0.5 truncate max-w-[80px]',
                  isSelected ? 'text-action-primaryText/90' : 'text-content-muted'
                )}
              >
                {cat.sublabel}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
};
