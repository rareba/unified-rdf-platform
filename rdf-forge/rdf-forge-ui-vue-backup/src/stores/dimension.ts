import { defineStore } from 'pinia';
import dimensionApi, { type Dimension, type DimensionValue } from '../api/dimension';

interface DimensionState {
  dimensions: Dimension[];
  currentDimension: Dimension | null;
  currentValues: DimensionValue[];
  totalDimensions: number;
  loading: boolean;
  error: string | null;
}

export const useDimensionStore = defineStore('dimension', {
  state: (): DimensionState => ({
    dimensions: [] as Dimension[],
    currentDimension: null as Dimension | null,
    currentValues: [] as DimensionValue[],
    totalDimensions: 0,
    loading: false,
    error: null as string | null,
  }),

  actions: {
    async fetchDimensions(params?: any) {
      this.loading = true;
      try {
        const response = await dimensionApi.list(params);
        this.dimensions = response.data.content;
        this.totalDimensions = response.data.totalElements;
      } catch (err: any) {
        this.error = err.message;
      } finally {
        this.loading = false;
      }
    },

    async fetchDimension(id: string) {
      this.loading = true;
      try {
        const response = await dimensionApi.get(id);
        this.currentDimension = response.data;
      } catch (err: any) {
        this.error = err.message;
      } finally {
        this.loading = false;
      }
    },

    async createDimension(dimension: Dimension) {
      try {
        const response = await dimensionApi.create(dimension);
        this.dimensions.push(response.data);
        return response.data;
      } catch (err: any) {
        this.error = err.message;
        throw err;
      }
    },

    async deleteDimension(id: string) {
      try {
        await dimensionApi.delete(id);
        this.dimensions = this.dimensions.filter(d => d.id !== id);
        if (this.currentDimension?.id === id) {
          this.currentDimension = null;
        }
      } catch (err: any) {
        this.error = err.message;
        throw err;
      }
    },

    async fetchValues(id: string, params?: any) {
      try {
        const response = await dimensionApi.getValues(id, params);
        this.currentValues = response.data.content;
        return response.data;
      } catch (err: any) {
        this.error = err.message;
        throw err;
      }
    },

    async importValues(id: string, file: File) {
      try {
        await dimensionApi.importCsv(id, file);
        await this.fetchValues(id);
      } catch (err: any) {
        this.error = err.message;
        throw err;
      }
    }
  },
});
