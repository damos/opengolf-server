package ca.dait.opengolf.controllers;

import ca.dait.opengolf.OpenGolfConstants;
import ca.dait.opengolf.services.CourseService;
import ca.dait.opengolf.services.CourseService.Course;
import ca.dait.opengolf.services.CourseService.CourseDetails;
import ca.dait.opengolf.services.CourseService.CourseSearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping(OpenGolfConstants.API.CONTEXT_ROOT + "/course")
public class CourseController {

    @Autowired
    protected CourseService courseService;

    @RequestMapping(value="{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CourseDetails> get(@PathVariable("id") String id) throws IOException {
        CourseDetails result = this.courseService.get(id);
        return new ResponseEntity<>(result, (result == null) ? HttpStatus.NOT_FOUND : HttpStatus.OK);
    }

    @RequestMapping(value="search", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public CourseSearchResult search(@RequestParam(value = "searchTerm", required = false) String searchTerm,
                                     @RequestParam(value = "lat", required = false) Double lat,
                                     @RequestParam(value = "lon", required = false) Double lon) throws IOException {
        //TODO: add input validation
        return this.courseService.search(searchTerm, lat, lon);
    }

    @PreAuthorize(OpenGolfConstants.Auth.IS_CONTRIBUTOR)
    @RequestMapping(method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Course> add(@RequestBody CourseDetails courseDetails) throws IOException {
        Course result = this.courseService.add(courseDetails);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
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
