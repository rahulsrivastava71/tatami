package fr.ippon.tatami.web.rest;

import com.yammer.metrics.annotation.Timed;
import fr.ippon.tatami.domain.User;
import fr.ippon.tatami.repository.UserTagRepository;
import fr.ippon.tatami.security.AuthenticationService;
import fr.ippon.tatami.service.TagMembershipService;
import fr.ippon.tatami.service.TimelineService;
import fr.ippon.tatami.service.TrendService;
import fr.ippon.tatami.service.UserService;
import fr.ippon.tatami.service.dto.StatusDTO;
import fr.ippon.tatami.service.util.DomainUtil;
import fr.ippon.tatami.web.rest.dto.Tag;
import fr.ippon.tatami.web.rest.dto.Trend;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * REST controller for managing tags.
 *
 * @author Julien Dubois
 */
@Controller
public class TagController {

    private final Log log = LogFactory.getLog(TagController.class);

    @Inject
    private TimelineService timelineService;

    @Inject
    private TagMembershipService tagMembershipService;

    @Inject
    private TrendService trendService;

    @Inject
    private UserTagRepository userTagRepository;

    @Inject
    private AuthenticationService authenticationService;

    @Inject
    private UserService userService;

    /**
     * GET  /rest/tags/{tagName}/tag_timeline -> get the latest status for a given tag
     */
    @RequestMapping(value = "/rest/tags/{tagName}/tag_timeline",
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    @Timed
    public Collection<StatusDTO> listStatusForTag(@PathVariable String tagName,
                                                  @RequestParam(required = false) Integer count,
                                                  @RequestParam(required = false) String since_id,
                                                  @RequestParam(required = false) String max_id) {

        if (log.isDebugEnabled()) {
            log.debug("REST request to get statuses for tag : " + tagName);
        }
        if (count == null) {
            count = 20;
        }
        try {
            return timelineService.getTagline(tagName, count, since_id, max_id);
        } catch (NumberFormatException e) {
            log.warn("Page size undefined ; sizing to default", e);
            return timelineService.getTagline(tagName, 20, since_id, max_id);
        }
    }

    /**
     * GET  /tags/popular -> get the list of popular tags
     */
    @RequestMapping(value = "/rest/tags/popular",
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    public Collection<Tag> getPopularTags() {
        User currentUser = authenticationService.getCurrentUser();
        String domain = DomainUtil.getDomainFromLogin(currentUser.getLogin());
        List<Trend> trends = trendService.getCurrentTrends(domain);
        Collection<String> followedTags = userTagRepository.findTags(currentUser.getLogin());
        Collection<Tag> tags = new ArrayList<Tag>();
        for (Trend trend : trends) {
            Tag tag = new Tag();
            tag.setName(trend.getTag());
            if (followedTags.contains(trend.getTag())) {
                tag.setFollowed(true);
            }
            tags.add(tag);
        }
        return tags;
    }


    @RequestMapping(value = "/rest/tags",
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    public Collection<Tag> getTags(@RequestParam(required = false, value = "popular") String popular,
                                      @RequestParam(required = false, value = "user") String username,
                                      @RequestParam(required = false, value = "search") String search) {
        Collection<Tag> tags = new ArrayList<Tag>();
        User currentUser = authenticationService.getCurrentUser();
        String domain = DomainUtil.getDomainFromLogin(currentUser.getLogin());
        Collection<String> followedTags = userTagRepository.findTags(currentUser.getLogin());
        Collection<String> tagNames;

        if(popular != null) {
            List<Trend> trends;
            User user = null;
            if(username != null) user = userService.getUserByUsername(username);
            if(user != null) {
                trendService.getTrendsForUser(user.getLogin());
                trends = trendService.getTrendsForUser(user.getLogin());
            }
            else {
                trends = trendService.getCurrentTrends(domain);
            }

            for (Trend trend : trends) {
                Tag tag = new Tag();
                tag.setName(trend.getTag());
                tag.setTrendingUp(trend.isTrendingUp());
                tags.add(tag);
            }
        }
        else if (search != null && !search.isEmpty()) {
            String prefix = search.toLowerCase();
            tagNames = trendService.searchTags(domain, prefix, 5);
            for (String tagName : tagNames) {
                Tag tag = new Tag();
                tag.setName(tagName);
                tags.add(tag);
            }
        }
        else {
            tagNames = userTagRepository.findTags(currentUser.getLogin());
            for (String tagName : tagNames) {
                Tag tag = new Tag();
                tag.setName(tagName);
                tags.add(tag);
            }
        }

        for (Tag tag : tags) {
            if (followedTags.contains(tag.getName())) {
                tag.setFollowed(true);
            };
        }

        return tags;
    }


    @RequestMapping(value = "/rest/tags/{tag}",
            method = RequestMethod.PUT,
            produces = "application/json")
    @ResponseBody
    public Tag updateTagNEW(@RequestBody Tag tag) {
        if(tag.isFollowed()){
            tagMembershipService.followTag(tag);
        } else {
            tagMembershipService.unfollowTag(tag);
        }
        return tag;
    }


    @RequestMapping(value = "/rest/tags/{tag}",
            method = RequestMethod.GET,
            produces = "application/json")
    @ResponseBody
    public Tag getTagNEW(@PathVariable("tag") String tagName) {
        User currentUser = authenticationService.getCurrentUser();
        Collection<String> followedTags = userTagRepository.findTags(currentUser.getLogin());
        Tag tag = new Tag();
        tag.setName(tagName);
        if (followedTags.contains(tagName)) {
            tag.setFollowed(true);
        }
        return tag;
    }
}
