import { useDispatch, useSelector } from 'react-redux';

import type { AppDispatch, RootState } from '@/store/store';

/** Use these instead of useDispatch / useSelector: they know the store's types. */
export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<RootState>();
