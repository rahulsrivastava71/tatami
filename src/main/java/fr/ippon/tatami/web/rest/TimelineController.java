package fr.ippon.tatami.web.rest;

import com.yammer.metrics.annotation.Timed;
import fr.ippon.tatami.domain.Group;
import fr.ippon.tatami.domain.User;
import fr.ippon.tatami.domain.status.StatusDetails;
import fr.ippon.tatami.security.AuthenticationService;
import fr.ippon.tatami.service.GroupService;
import fr.ippon.tatami.service.StatusUpdateService;
import fr.ippon.tatami.service.TimelineService;
import fr.ippon.tatami.service.dto.StatusDTO;
import fr.ippon.tatami.service.exception.ArchivedGroupException;
import fr.ippon.tatami.service.exception.ReplyStatusException;
import fr.ippon.tatami.web.rest.dto.ActionStatus;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import java.util.Collection;

/**
 * REST controller for managing status.
 *
 * @author Julien Dubois
 */
@Controller
public class TimelineController {

    private final Log log = LogFactory.getLog(TimelineController.class);

    @Inject
    private TimelineService timelineService;

    @Inject
    private StatusUpdateService statusUpdateService;

    @Inject
    private GroupService groupService;

    @Inject
    private AuthenticationService authenticationService;

    /**
     * GET  /statuses/details/:id -> returns the details for a status, specified by the id parameter
     */
    @RequestMapping(value = "/rest/statuses/details/{statusId}",
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    public StatusDetails getStatusDetails(@PathVariable("statusId") String statusId) {
        if (log.isDebugEnabled()) {
            log.debug("REST request to get status details Id : " + statusId);
        }
        return timelineService.getStatusDetails(statusId);
    }

    /**
     * GET  /statuses/home_timeline -> get the latest statuses from the current user
     */
    @RequestMapping(value = "/rest/statuses/home_timeline",
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    @Timed
    public Collection<StatusDTO> listStatus(@RequestParam(required = false) Integer count,
                                            @RequestParam(required = false) String since_id,
                                            @RequestParam(required = false) String max_id) {
        if (count == null || count == 0) {
            count = 20; //Default value
        }
        try {
            return timelineService.getTimeline(count, since_id, max_id);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * GET  /statuses/user_timeline?screen_name=jdubois -> get the latest statuses from user "jdubois"
     */
    @RequestMapping(value = "/rest/statuses/{username}/timeline",
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    public Collection<StatusDTO> listStatusForUser(@PathVariable("username") String username,
                                                   @RequestParam(required = false) Integer count,
                                                   @RequestParam(required = false) String since_id,
                                                   @RequestParam(required = false) String max_id) {

        if (count == null || count == 0) {
            count = 20; //Default value
        }
        if (log.isDebugEnabled()) {
            log.debug("REST request to get someone's status (username=" + username + ").");
        }
        try {
            return timelineService.getUserline(username, count, since_id, max_id);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    @RequestMapping(value = "/rest/statuses/{statusId}",
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    public StatusDTO getStatus(@PathVariable("statusId") String statusId) {
        if (log.isDebugEnabled()) {
            log.debug("REST request to get status Id : " + statusId);
        }
        return timelineService.getStatus(statusId);
    }


    @RequestMapping(value = "/rest/statuses/{statusId}",
            method = RequestMethod.DELETE)
    public void deleteStatus(@PathVariable("statusId") String statusId) {
        if (log.isDebugEnabled()) {
            log.debug("REST request to get status Id : " + statusId);
        }
        timelineService.removeStatus(statusId);
    }

    @RequestMapping(value = "/rest/statuses/{statusId}",
            method = RequestMethod.PATCH)
    @ResponseBody
    public StatusDTO updateStatus(@RequestBody ActionStatus action, @PathVariable("statusId") String statusId) {
        try {
            StatusDTO status = timelineService.getStatus(statusId);
            if(action.isFavorite() != null && status.isFavorite() != action.isFavorite()){
                if(action.isFavorite()){
                    timelineService.addFavoriteStatus(statusId);
                }
                else {
                    timelineService.removeFavoriteStatus(statusId);
                }
                status.setFavorite(action.isFavorite());
            }
            if(action.isShared() != null && action.isShared()){
                timelineService.shareStatus(statusId);
            }
            if(action.isAnnounced() != null && action.isAnnounced()){
                timelineService.announceStatus(statusId);
            }
            return status;
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            return null;
        }
    }


    /**
     * POST /statuses/update -> create a new Status
     */
    @RequestMapping(value = "/rest/statuses/",
            method = RequestMethod.POST,
            produces = "application/json")
    @Timed
    public String postStatus(@RequestBody StatusDTO status, HttpServletResponse response) throws ArchivedGroupException, ReplyStatusException {
        if (log.isDebugEnabled()) {
            log.debug("REST request to add status : " + status.getContent());
        }
        String escapedContent = StringEscapeUtils.escapeHtml(status.getContent());
        Collection<String> attachmentIds = status.getAttachmentIds();

        if (status.getReplyTo() != null && !status.getReplyTo().isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Creating a reply to : " + status.getReplyTo());
            }
            statusUpdateService.replyToStatus(escapedContent, status.getReplyTo());
        }
        else if(status.isStatusPrivate() || status.getGroupId() == null || status.getGroupId().equals("")) {
            if (log.isDebugEnabled()) {
                log.debug("Private status");
            }
            statusUpdateService.postStatus(escapedContent, status.isStatusPrivate(), attachmentIds);
        } else {
            User currentUser = authenticationService.getCurrentUser();
            Collection<Group> groups = groupService.getGroupsForUser(currentUser);
            Group group = null;
            for (Group testGroup : groups) {
                if (testGroup.getGroupId().equals(status.getGroupId())) {
                    group = testGroup;
                    break;
                }
            }
            if (group == null) {
                if (log.isInfoEnabled()) {
                    log.info("Permission denied! User " + currentUser.getLogin() + " tried to access " +
                            "group ID = " + status.getGroupId());
                }
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            } else if (group.isArchivedGroup()) {
                if (log.isInfoEnabled()) {
                    log.info("Archived group! User " + currentUser.getLogin() + " tried to post a message to archived " +
                            "group ID = " + status.getGroupId());
                }
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            } else {
                statusUpdateService.postStatusToGroup(escapedContent, group, attachmentIds);
            }
        }
        return "{}";
    }
}
