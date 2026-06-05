package com.edu.mental.service;

import com.edu.mental.dto.QuestionnaireFullDto;
import com.edu.mental.entity.Question;

import java.util.List;

public interface QuestionService {

    List<Question> listByQuestionnaire(Long questionnaireId);

    QuestionnaireFullDto getFull(Long questionnaireId);

    /**
 * Persist the given Question entity and return the saved instance.
 *
 * @param question the Question entity to persist; may be a new entity or an existing one to be merged
 * @return the persisted Question instance, potentially with generated identifiers or updated audit fields
 */
Question save(Question question);

    /**
 * Persists multiple Question entities and associates them with the specified questionnaire.
 *
 * @param questionnaireId the ID of the questionnaire to associate the saved questions with
 * @param questions the list of Question entities to persist
 */
void saveBatch(Long questionnaireId, List<Question> questions);

    /**
 * Updates an existing Question using the values contained in the provided entity.
 *
 * @param question the Question entity whose identifier determines which record to update; its fields contain the new values to persist
 */
void update(Question question);

    /**
 * Deletes the Question identified by the given questionId.
 *
 * @param questionId the identifier of the Question to remove
 */
void delete(Long questionId);

    /**
 * Deletes all Question records associated with the specified questionnaire.
 *
 * @param questionnaireId the identifier of the questionnaire whose questions will be removed
 */
void deleteByQuestionnaireId(Long questionnaireId);
}
