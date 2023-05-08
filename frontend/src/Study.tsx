import {Link, useParams} from "react-router-dom";
import React, {useEffect, useState} from "react";
import {Button, Col, Row} from "react-bootstrap";
import {
    PlannedModuleGroup,
    PlannedStudyCourse,
    PlannedStudyModule,
    PlannedStudySegment,
    PlanningControllerService,
    StudyData,
    StudyDataControllerService,
    StudyPlan
} from "./generated";
import {CourseList} from "./CourseList";

function StudyPlanComponent(studyPlan: StudyPlan) {
    const allCourses = studyPlan.studySegments
        .flatMap(segment => segment.moduleGroups)
        .flatMap(moduleGroup => moduleGroup.modules)
        .flatMap(module => module.courses);

    const totalEcts = allCourses
        .map(course => course.ects ? course.ects : 0)
        .reduce((a, b) => a + b, 0);
    const totalEffort = allCourses
        .map(course => course.effort ? course.effort : 0)
        .reduce((a, b) => a + b, 0);

    return <div>
        <h1>Segments</h1>
        {studyPlan.studySegments.map(segment => <ShowStudySegment key={segment.id + segment.name} {...segment}/>)}
        <h1>Total</h1>
        <div>{totalEcts} ECTS</div>
        <div>{totalEffort} effort adjusted ECTS</div>
    </div>;
}

export function Study() {
    const {number} = useParams();
    const [studyData, setStudyData] = useState<StudyData>();
    const [error, setError] = useState<string | undefined>();
    const [studyPlan, setStudyPlan] = useState<StudyPlan | undefined>();

    const loadStudyPlan = (number: string) => {
        setStudyPlan(undefined);
        PlanningControllerService.planStudy(number).then(studyPlan => {
            setStudyPlan(studyPlan);
        });
    };

    useEffect(() => {
        if (number !== undefined) {
            loadStudyPlan(number);
        }
    }, [number]);

    useEffect(() => {
        if (number !== undefined) {
            setStudyData(undefined);
            StudyDataControllerService.study(number)
                .then(study => {
                    setStudyData(study);
                })
                .catch(error => {
                    setError(error.toString());
                });
        }
    }, [number]);

    if (error !== undefined) {
        return <div>{error}</div>
    } else if (studyData === undefined) {
        return <div>Loading...</div>
    } else {
        return <div>
            <Link to="/"><Button variant="link">Back to study list</Button></Link>
            <h1>{studyData.study.name}</h1>
            <Row>
                <Col>
                    <Button variant="secondary" disabled={!studyPlan}
                            onClick={() => {
                                if (number) loadStudyPlan(number)
                            }}>Reload plan</Button>
                    {!studyPlan ? <div>Study plan loading...</div> : <StudyPlanComponent {...studyPlan}/>}
                </Col>
                <Col>
                    Courses
                    <CourseList {...studyData}/>
                </Col>
            </Row>
        </div>
    }
}

function ShowCourse(course: PlannedStudyCourse): JSX.Element {
    return <div>
        {course.name} ({course.ects ? `${course.ects} ECTS` : ''}
        {course.effort && course.ects !== course.effort ? ` (${course.effort} effort adjusted ECTS)` : ''})
    </div>;
}

function ShowModule(module: PlannedStudyModule): JSX.Element {
    const totalEcts = module.courses
        .reduce((a, b) => a + (b.ects ? b.ects : 0), 0);
    const totalEffort = module.courses
        .reduce((a, b) => a + (b.effort ? b.effort : 0), 0);


    return <div>
        {module.name} ({totalEcts} ECTS{totalEcts !== totalEffort ? `, ${totalEffort} effort adjusted ECTS` : ''})
        <ul>
            {module.courses.map(course => <li key={course.id}><ShowCourse {...course}/></li>)}
        </ul>
    </div>;
}

function ShowModuleGroup(moduleGroup: PlannedModuleGroup): JSX.Element {
    return <div>
        {moduleGroup.name}
        <ul>
            {moduleGroup.modules.map(module => <li key={module.id}><ShowModule {...module}/></li>)}
        </ul>
    </div>;
}

function ShowStudySegment(studySegment: PlannedStudySegment) {
    const allCourses = studySegment.moduleGroups
        .flatMap(moduleGroup => moduleGroup.modules)
        .flatMap(module => module.courses);
    const ects = allCourses.map(course => course.ects ? course.ects : 0).reduce((a, b) => a + b, 0);

    return <>
        <div>{studySegment.name} ({ects} ECTS)</div>
        <ul>
            {
                studySegment.moduleGroups.map(moduleGroup => <li key={moduleGroup.id}>
                    <ShowModuleGroup {...moduleGroup}/>
                </li>)
            }
        </ul>
    </>
}
