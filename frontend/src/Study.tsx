import {Link, useParams} from "react-router-dom";
import React, {useEffect, useState} from "react";
import {StudyData, StudyDataControllerService, StudySegment} from "./generated";

export function Study() {
    const {number} = useParams();
    const [studyData, setStudyData] = useState<StudyData>();
    const [error, setError] = useState<string | undefined>();

    useEffect(() => {
        if (number !== undefined) {
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
            <Link to="/">Back to study list</Link>
            <h1>{studyData.study.name}</h1>
            <div>
                ECTS required: {studyData.study.ects}
            </div>
            <h2>Segments</h2>
            <ul>
                {studyData.segments.map(segment => <li key={segment.id}>
                    <ShowStudySegment {...segment}/>
                </li>)}
            </ul>
        </div>
    }
}

function ShowStudySegment(studySegment: StudySegment) {
    return <>
        <div>{studySegment.name} ({studySegment.ects} ECTS)</div>
        <ul>
            {
                studySegment.moduleGroups.map(moduleGroup => <li key={moduleGroup.id}>
                    <div>{moduleGroup.name}</div>
                </li>)
            }
        </ul>
    </>
}
