import { CategoryCard } from '@/components/business/Storefront';
import { AsyncContent } from '@/components/common/AsyncContent';
import { EmptyState } from '@/components/common/EmptyState';
import { Skeleton } from '@/components/common/Loader';
import { PageHeader } from '@/components/common/PageHeader';
import { useStoreCategories } from '@/hooks/api/storefrontHooks';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

const GRID = 'grid gap-6 sm:grid-cols-2 lg:grid-cols-4';

/** Every food category (the home page's "What would you like to eat?"); each opens the dishes of that category. */
export default function StoreCategoriesPage() {
  const categories = useStoreCategories();
  const count = categories.data?.length;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Food categories"
        subtitle={
          count === undefined
            ? 'What would you like to eat?'
            : `${count} ${count === 1 ? 'category' : 'categories'} to choose from`
        }
      />
      <AsyncContent
        isLoading={categories.isPending}
        error={categories.error}
        isEmpty={categories.data?.length === 0}
        errorTitle={ERROR_MESSAGES.CATEGORIES_LOAD_FAILED}
        onRetry={() => void categories.refetch()}
        loading={
          <div className={GRID}>
            {Array.from({ length: 8 }, (_, index) => (
              <Skeleton key={index} className="h-60 rounded-2xl bg-surface" />
            ))}
          </div>
        }
        empty={<EmptyState title="There are no food categories yet" />}
      >
        <ul className={GRID}>
          {categories.data?.map((category) => (
            <li key={category.id}>
              <CategoryCard category={category} />
            </li>
          ))}
        </ul>
      </AsyncContent>
    </div>
  );
}
