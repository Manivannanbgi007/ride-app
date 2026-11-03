import { Plus } from 'lucide-react';
import { useNavigate } from 'react-router';

import { CouponFilterBar, CouponTable } from '@/components/business/Coupon';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { PageHeader } from '@/components/common/PageHeader';
import { PagedResults } from '@/components/common/PagedResults';
import { useCoupons } from '@/hooks/api/couponHooks';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import {
  couponFiltersChanged,
  couponFiltersCleared,
  couponPageChanged,
  selectCouponFilters,
  selectCouponPage,
} from '@/store/slices/couponListSlice';
import { DEFAULT_PAGE_SIZE, ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** "Coupons": every coupon with how much it is used and whether it can be used today. */
export default function CouponListPage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const filters = useAppSelector(selectCouponFilters);
  const page = useAppSelector(selectCouponPage);
  const coupons = useCoupons(filters, page, DEFAULT_PAGE_SIZE);
  const clearFilters = () => dispatch(couponFiltersCleared());

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Coupons"
        subtitle="Create, change and pause coupons, and see how they are used"
        actions={
          <Button
            onClick={() => void navigate(ROUTES.couponNew)}
            leftIcon={<Plus aria-hidden="true" className="size-4" />}
          >
            New coupon
          </Button>
        }
      />

      <Card>
        <CouponFilterBar
          filters={filters}
          onChange={(changes) => dispatch(couponFiltersChanged(changes))}
          onClear={clearFilters}
        />
      </Card>

      <Card>
        <PagedResults
          query={coupons}
          errorTitle={ERROR_MESSAGES.COUPONS_LOAD_FAILED}
          emptyTitle="No coupons yet"
          filteredEmptyTitle="No coupons match these filters"
          hasFilters={Object.keys(filters).some((key) => key !== 'sort')}
          onClearFilters={clearFilters}
          onPageChange={(newPage) => dispatch(couponPageChanged(newPage))}
          columns={5}
        >
          {(rows, isUpdating) => <CouponTable coupons={rows} isUpdating={isUpdating} />}
        </PagedResults>
      </Card>
    </div>
  );
}
