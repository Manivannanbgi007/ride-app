import { useState } from 'react';

export type FoodViewMode = 'grid' | 'list';

export const FOOD_VIEW_STORAGE_KEY = 'foods.view';

function readSaved(): FoodViewMode {
  try {
    return localStorage.getItem(FOOD_VIEW_STORAGE_KEY) === 'list' ? 'list' : 'grid';
  } catch {
    return 'grid';
  }
}

/** The food list's look (cards or table), kept in this browser; cards when nothing is saved or storage is blocked. */
export function useFoodViewMode(): [FoodViewMode, (mode: FoodViewMode) => void] {
  const [mode, setMode] = useState<FoodViewMode>(readSaved);
  const change = (next: FoodViewMode) => {
    setMode(next);
    try {
      localStorage.setItem(FOOD_VIEW_STORAGE_KEY, next);
    } catch {
      // Private window or blocked storage: the choice just is not remembered
    }
  };
  return [mode, change];
}
