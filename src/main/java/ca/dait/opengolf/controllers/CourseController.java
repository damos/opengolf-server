package ca.dait.opengolf.controllers;

import ca.dait.opengolf.OpenGolfConstants;
import ca.dait.opengolf.services.CourseService;
import ca.dait.opengolf.services.CourseService.Course;
import ca.dait.opengolf.services.CourseService.CourseDetails;
import ca.dait.opengolf.services.CourseService.CourseSearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping(OpenGolfConstants.API.CONTEXT_ROOT + "/course")
public class CourseController {

    @Autowired
    CourseService courseService;

    @RequestMapping(value="{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public CourseDetails get(@PathVariable("id") String id) throws IOException {
        return this.courseService.get(id);
    }

    @RequestMapping(value="search", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public CourseSearchResult search(@RequestParam("searchTerm") String searchTerm) throws IOException {
        return this.courseService.search(searchTerm);
    }

    @PreAuthorize(OpenGolfConstants.Auth.IS_CONTRIBUTOR)
    @RequestMapping(method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Course add(HttpServletResponse response, @RequestBody CourseDetails courseDetails) throws IOException {
        Course result = this.courseService.add(courseDetails);
        response.setStatus(HttpServletResponse.SC_CREATED);
        return result;
    }

    @PreAuthorize(OpenGolfConstants.Auth.IS_CONTRIBUTOR)
    @RequestMapping(value="{id}", method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE)
    public void update(@PathVariable("id") String id, @RequestBody CourseDetails courseDetails) throws IOException {
        this.courseService.update(id, courseDetails);
    }

    @PreAuthorize(OpenGolfConstants.Auth.IS_CONTRIBUTOR)
    @RequestMapping(value="{id}", method = RequestMethod.DELETE)
    public void delete(@PathVariable("id") String id) throws IOException {
        this.courseService.delete(id);
    }
}
