import { mockExercises } from "../constants/mockData";
import { useMockApi } from "./serviceMode";

// Mock adapter preserves current rendering and interaction timing.
export const exerciseService = {
  getExercises: () => useMockApi ? mockExercises.map(item => [...item] as typeof item) : [],
};
