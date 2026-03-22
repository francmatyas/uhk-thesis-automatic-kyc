import { makeCrudApi } from "@/api/makeCrudApi";

const journeyTemplatesApi = makeCrudApi("/provider/journey-templates");

export const fetchJourneyTemplate = journeyTemplatesApi.fetchOne;
export const createJourneyTemplate = journeyTemplatesApi.create;
export const updateJourneyTemplate = journeyTemplatesApi.update;
export const deleteJourneyTemplate = journeyTemplatesApi.remove;
