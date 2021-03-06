/*
 * All GTAS code is Copyright 2016, Unisys Corporation.
 * 
 * Please see LICENSE.txt for details.
 */
package gov.gtas.querybuilder.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.Errors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.gtas.model.Flight;
import gov.gtas.model.Passenger;
import gov.gtas.model.User;
import gov.gtas.model.udr.json.QueryObject;
import gov.gtas.querybuilder.exceptions.InvalidQueryException;
import gov.gtas.querybuilder.exceptions.InvalidQueryRepositoryException;
import gov.gtas.querybuilder.exceptions.InvalidUserRepositoryException;
import gov.gtas.querybuilder.exceptions.QueryAlreadyExistsException;
import gov.gtas.querybuilder.exceptions.QueryAlreadyExistsRepositoryException;
import gov.gtas.querybuilder.exceptions.QueryDoesNotExistException;
import gov.gtas.querybuilder.exceptions.QueryDoesNotExistRepositoryException;
import gov.gtas.querybuilder.model.IUserQueryResult;
import gov.gtas.querybuilder.model.QueryRequest;
import gov.gtas.querybuilder.model.UserQuery;
import gov.gtas.querybuilder.model.UserQueryRequest;
import gov.gtas.querybuilder.model.UserQueryResult;
import gov.gtas.querybuilder.repository.QueryBuilderRepository;
import gov.gtas.querybuilder.validation.util.QueryValidationUtils;
import gov.gtas.querybuilder.vo.FlightQueryVo;
import gov.gtas.querybuilder.vo.PassengerQueryVo;
import gov.gtas.services.PassengerService;
import gov.gtas.services.dto.FlightsPageDto;
import gov.gtas.services.dto.PassengersPageDto;
import gov.gtas.vo.passenger.FlightVo;
import gov.gtas.vo.passenger.PassengerVo;

@Service
public class QueryBuilderService {
    private static final Logger logger = LoggerFactory.getLogger(QueryBuilderService.class);
    @Autowired
    private QueryBuilderRepository queryRepository;
    @Autowired
    private PassengerService passengerService;
    
    /**
     * Persists a user defined query to the database
     * @param userId
     * @param queryRequest
     * @return
     * @throws QueryAlreadyExistsException
     * @throws InvalidQueryException
     */
    public IUserQueryResult saveQuery(String userId, UserQueryRequest queryRequest) throws QueryAlreadyExistsException, InvalidQueryException {
        IUserQueryResult result = new UserQueryResult();
        
        try {
            logger.debug("Create query " + queryRequest.getTitle() + " by " + userId);
            result = mapToQueryResult(queryRepository.saveQuery(createUserQuery(userId, queryRequest)));
        } catch(QueryAlreadyExistsRepositoryException e) {
            throw new QueryAlreadyExistsException(e.getMessage(), queryRequest);
        } catch (InvalidUserRepositoryException | InvalidQueryException | InvalidQueryRepositoryException | IOException | IllegalArgumentException e) {
            throw new InvalidQueryException(e.getMessage(), queryRequest);
        }
        
        return result;
    }

    public IUserQueryResult editQuery(String userId, UserQueryRequest queryRequest) throws QueryAlreadyExistsException, QueryDoesNotExistException, 
        InvalidQueryException {
        IUserQueryResult result = new UserQueryResult();
        
        try {
            logger.debug("Edit query " + queryRequest.getTitle() + " by " + userId);
            result = mapToQueryResult(queryRepository.editQuery(createUserQuery(userId, queryRequest)));
        } catch(QueryAlreadyExistsRepositoryException e) {
            throw new QueryAlreadyExistsException(e.getMessage(), queryRequest);
        } catch (QueryDoesNotExistRepositoryException e) {
            throw new QueryDoesNotExistException(e.getMessage(), queryRequest);
        } catch (InvalidUserRepositoryException | InvalidQueryException | InvalidQueryRepositoryException | IOException | IllegalArgumentException e) {
            throw new InvalidQueryException(e.getMessage(), queryRequest);
        } 
        
        return result;
    }
    
    public List<IUserQueryResult> listQueryByUser(String userId) throws InvalidQueryException {
        List<IUserQueryResult> result = new ArrayList<>();
        
        try {
            logger.debug("List query by " + userId);
            result = mapToResultList(queryRepository.listQueryByUser(userId));
        } catch (InvalidUserRepositoryException | InvalidQueryException e) {
            throw new InvalidQueryException(e.getMessage(), null);
        }
        
        return result;
    }
    
    public void deleteQuery(String userId, int id) throws QueryDoesNotExistException, InvalidQueryException {
        
        try {
            logger.debug("Delete query id: " + id + " by " + userId);
            queryRepository.deleteQuery(userId, id);
        } catch (QueryDoesNotExistRepositoryException e) {
            throw new QueryDoesNotExistException(e.getMessage(), null);
        } catch (InvalidUserRepositoryException e) {
            throw new InvalidQueryException(e.getMessage(), null);
        } 
    }
        
    public FlightsPageDto runFlightQuery(QueryRequest queryRequest) throws InvalidQueryException {
        List<FlightVo> flightList = new ArrayList<>();
        long totalCount = 0;
        
        // validate queryRequest
        if(queryRequest == null) {
            throw new InvalidQueryException("Error: query is null", queryRequest);
        }
        
        Errors errors = QueryValidationUtils.validateQueryObject(queryRequest.getQuery());
        if(errors != null && errors.hasErrors()) {
            String errorMsg = QueryValidationUtils.getErrorString(errors);
            logger.info(errorMsg, new InvalidQueryException(errorMsg, queryRequest.getQuery()));
            throw new InvalidQueryException(errorMsg, queryRequest.getQuery());
        }
                
        try {
            FlightQueryVo flights = queryRepository.getFlightsByDynamicQuery(queryRequest);
            
            if(flights == null) {
                return new FlightsPageDto(flightList, totalCount);
            }
            
            totalCount = flights.getTotalFlights();
                        
            for(Flight flight : flights.getFlights()) {
                if(flight != null && flight.getId() > 0) {
                    FlightVo flightVo = new FlightVo();
                    
                    BeanUtils.copyProperties(flight, flightVo);
                    flightList.add(flightVo);
                }
            }
        } catch (InvalidQueryRepositoryException | IllegalArgumentException e) {
            throw new InvalidQueryException(e.getMessage(), queryRequest.getQuery());
        } 
        
        return new FlightsPageDto(flightList, totalCount);
    }
    
    public PassengersPageDto runPassengerQuery(QueryRequest queryRequest) throws InvalidQueryException {
        List<PassengerVo> passengerList = new ArrayList<>();
        long totalCount = 0;
        
        // validate queryRequest
        if(queryRequest == null) {
            throw new InvalidQueryException("Error: query is null", queryRequest);
        }
        
        Errors errors = QueryValidationUtils.validateQueryObject(queryRequest.getQuery());
        if(errors != null && errors.hasErrors()) {
            String errorMsg = QueryValidationUtils.getErrorString(errors);
            logger.info(errorMsg, new InvalidQueryException(errorMsg, queryRequest.getQuery()));
            throw new InvalidQueryException(errorMsg, queryRequest.getQuery());
        }
            
        try {
            PassengerQueryVo resultList = queryRepository.getPassengersByDynamicQuery(queryRequest);
            
            if(resultList == null) {
                return new PassengersPageDto(passengerList, totalCount);
            }
            
            totalCount = resultList.getTotalPassengers();
            
            for (Object[] result : resultList.getResult()) {
                Passenger passenger = (Passenger) result[1];
                Flight flight = (Flight) result[2];
                PassengerVo vo = new PassengerVo();
                
                // passenger information
                BeanUtils.copyProperties(passenger, vo);
                passengerList.add(vo);

                // populate with hits information
                passengerService.fillWithHitsInfo(vo, flight.getId(), passenger.getId());
                
                // flight information
                vo.setFlightId(flight.getId() != null ? String.valueOf(flight.getId()) : "");
                vo.setFlightNumber(flight.getFlightNumber());
                vo.setCarrier(flight.getCarrier());
                vo.setFlightOrigin(flight.getOrigin());
                vo.setFlightDestination(flight.getDestination());
                vo.setEtd(flight.getEtd());
                vo.setEta(flight.getEta());
            }
        } catch (InvalidQueryRepositoryException | IllegalArgumentException e) {
            throw new InvalidQueryException(e.getMessage(), queryRequest.getQuery());
        }
        
        return new PassengersPageDto(passengerList, totalCount);
    }
    
    private UserQuery createUserQuery(String userId, UserQueryRequest req) throws JsonProcessingException {
        UserQuery query = new UserQuery();
        ObjectMapper mapper = new ObjectMapper();
        
        if(req != null) {
            User user = new User();
            user.setUserId(userId);
            
            query.setId(req.getId());
            query.setCreatedBy(user);
            query.setTitle(req.getTitle());
            query.setDescription(req.getDescription());
            query.setQueryText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(req.getQuery()));
        }
        
        return query;
    }
    
    private IUserQueryResult mapToQueryResult(UserQuery query) throws InvalidQueryException {
        IUserQueryResult result = new UserQueryResult();
        ObjectMapper mapper = new ObjectMapper();
        
        result.setId(query.getId());
        result.setTitle(query.getTitle());
        result.setDescription(query.getDescription());
        try {
            if(query.getQueryText() != null && !query.getQueryText().isEmpty()) {
                result.setQuery(mapper.readValue(query.getQueryText(), QueryObject.class));
            }
        } catch (IOException e) {
            throw new InvalidQueryException(e.getMessage(), query);
        }
        
        return result;
    }
    
    private List<IUserQueryResult> mapToResultList(List<UserQuery> queryList) throws InvalidQueryException {
        List<IUserQueryResult> resultList = new ArrayList<>();
        
        if(queryList != null && queryList.size() > 0) {
            for(UserQuery query : queryList) {
                try {
                    resultList.add(mapToQueryResult(query));
                } catch (InvalidQueryException e) {
                    throw new InvalidQueryException(e.getMessage(), queryList);
                }
            }
        }
        
        return resultList;
    }
    
}

