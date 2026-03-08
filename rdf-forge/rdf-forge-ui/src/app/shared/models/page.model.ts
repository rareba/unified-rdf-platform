export interface Page<T> {
  content: T[];
  pageable: Pageable;
  totalElements: number;
  totalPages: number;
  last: boolean;
  size: number;
  number: number;
  sort: Sort;
  numberOfElements: number;
  first: boolean;
  empty: boolean;
}

export interface Pageable {
  pageNumber: number;
  pageSize: number;
  sort: Sort;
  offset: number;
  page: number;
  unpaged: boolean;
  paged: boolean;
}

export interface Sort {
  empty: boolean;
  sorted: boolean;
  unsorted: boolean;
}

export interface PageRequest {
  page: number;
  size: number;
  sort?: string;
  direction?: 'asc' | 'desc';
}

export function emptyPage<T>(): Page<T> {
  return {
    content: [],
    pageable: {
      pageNumber: 0,
      pageSize: 20,
      sort: { empty: true, sorted: false, unsorted: true },
      offset: 0,
      page: 0,
      unpaged: false,
      paged: true
    },
    totalElements: 0,
    totalPages: 0,
    last: true,
    size: 20,
    number: 0,
    sort: { empty: true, sorted: false, unsorted: true },
    numberOfElements: 0,
    first: true,
    empty: true
  };
}
